package com.homework2.support.service;

import com.homework2.support.TestDataFactory;
import com.homework2.support.api.dto.TicketImportSummaryResponse;
import com.homework2.support.api.dto.TicketRequest;
import com.homework2.support.importer.ImportRecord;
import com.homework2.support.importer.MalformedImportFileException;
import com.homework2.support.importer.TicketImportParser;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketImportServiceTest {
    @Mock
    private TicketImportParser parser;
    @Mock
    private TicketService ticketService;
    @Mock
    private Validator validator;

    private TicketImportService importService;

    @BeforeEach
    void setUp() {
        importService = new TicketImportService(List.of(parser), ticketService, validator);
    }

    @Test
    void importTicketsRejectsNullAndEmptyFiles() {
        assertThatThrownBy(() -> importService.importTickets(null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Import file is required and cannot be empty");

        MockMultipartFile emptyFile = new MockMultipartFile("file", "tickets.csv", "text/csv", new byte[0]);
        assertThatThrownBy(() -> importService.importTickets(emptyFile))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Import file is required and cannot be empty");
    }

    @Test
    void importTicketsRejectsUnsupportedFileType() {
        MockMultipartFile file = new MockMultipartFile("file", "tickets.txt", "text/plain", "data".getBytes(StandardCharsets.UTF_8));
        when(parser.supports(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> importService.importTickets(file))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unsupported file type");
    }

    @Test
    void importTicketsConvertsMalformedParserExceptionToBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "tickets.csv", "text/csv", "data".getBytes(StandardCharsets.UTF_8));
        when(parser.supports("tickets.csv", "text/csv")).thenReturn(true);
        when(parser.parse(any(byte[].class))).thenThrow(new MalformedImportFileException("Malformed CSV file"));

        assertThatThrownBy(() -> importService.importTickets(file))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Malformed CSV file");
    }

    @Test
    void importTicketsConvertsIOExceptionToBadRequest() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("tickets.csv");
        when(file.getContentType()).thenReturn("text/csv");
        when(parser.supports("tickets.csv", "text/csv")).thenReturn(true);
        when(file.getBytes()).thenThrow(new IOException("wrapper", new IllegalStateException()));

        assertThatThrownBy(() -> importService.importTickets(file))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unable to read import file: IllegalStateException");
    }

    @Test
    void importTicketsAggregatesParseValidationAndCreationErrors() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "tickets.csv", "text/csv", "data".getBytes(StandardCharsets.UTF_8));
        when(parser.supports("tickets.csv", "text/csv")).thenReturn(true);

        TicketRequest invalidRequest = TestDataFactory.validTicketRequest();
        TicketRequest failingRequest = TestDataFactory.validTicketRequest("Billing issue", "Need a billing refund due to duplicate invoice charges.");
        TicketRequest successRequest = TestDataFactory.validTicketRequest("Feature request", "Please add dark mode support for the dashboard soon.");

        when(parser.parse(any(byte[].class))).thenReturn(List.of(
                ImportRecord.failure(1, "Record parse error"),
                ImportRecord.success(2, invalidRequest),
                ImportRecord.success(3, failingRequest),
                ImportRecord.success(4, successRequest)
        ));
        ConstraintViolation<TicketRequest> subjectViolation = violation("subject", "must not be blank");
        ConstraintViolation<TicketRequest> emailViolation = violation("customerEmail", "must be a well-formed email address");
        when(validator.validate(invalidRequest)).thenReturn(Set.of(subjectViolation, emailViolation));
        when(validator.validate(failingRequest)).thenReturn(Set.of());
        when(validator.validate(successRequest)).thenReturn(Set.of());
        doThrow(new RuntimeException("create failed", new IllegalStateException()))
                .when(ticketService).createTicket(failingRequest);
        when(ticketService.createTicket(successRequest)).thenReturn(null);

        TicketImportSummaryResponse summary = importService.importTickets(file);

        assertThat(summary.totalRecords()).isEqualTo(4);
        assertThat(summary.successful()).isEqualTo(1);
        assertThat(summary.failed()).isEqualTo(3);
        assertThat(summary.errors()).hasSize(3);
        assertThat(summary.errors().get(0).recordNumber()).isEqualTo(1);
        assertThat(summary.errors().get(1).message()).startsWith("customerEmail: must be a well-formed email address");
        assertThat(summary.errors().get(1).message()).contains("; subject: must not be blank");
        assertThat(summary.errors().get(2).message()).isEqualTo("Failed to import ticket: IllegalStateException");
    }

    private static ConstraintViolation<TicketRequest> violation(String field, String message) {
        @SuppressWarnings("unchecked")
        ConstraintViolation<TicketRequest> violation = (ConstraintViolation<TicketRequest>) mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn(field);
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn(message);
        return violation;
    }
}
