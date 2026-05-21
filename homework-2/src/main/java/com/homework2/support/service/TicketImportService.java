package com.homework2.support.service;

import com.homework2.support.api.dto.TicketImportErrorResponse;
import com.homework2.support.api.dto.TicketImportSummaryResponse;
import com.homework2.support.api.dto.TicketRequest;
import com.homework2.support.importer.ImportRecord;
import com.homework2.support.importer.MalformedImportFileException;
import com.homework2.support.importer.TicketImportParser;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TicketImportService {
    private final List<TicketImportParser> ticketImportParsers;
    private final TicketService ticketService;
    private final Validator validator;

    public TicketImportService(
            List<TicketImportParser> ticketImportParsers,
            TicketService ticketService,
            Validator validator
    ) {
        this.ticketImportParsers = ticketImportParsers;
        this.ticketService = ticketService;
        this.validator = validator;
    }

    public TicketImportSummaryResponse importTickets(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Import file is required and cannot be empty.");
        }

        TicketImportParser parser = resolveParser(file.getOriginalFilename(), file.getContentType());
        List<ImportRecord> records = parseRecords(parser, file);

        List<TicketImportErrorResponse> errors = new ArrayList<>();
        int successCount = 0;

        for (ImportRecord record : records) {
            if (record.hasParseError()) {
                errors.add(new TicketImportErrorResponse(record.recordNumber(), record.parseError()));
                continue;
            }

            TicketRequest ticketRequest = record.ticketRequest();
            String validationError = validateRecord(ticketRequest);
            if (validationError != null) {
                errors.add(new TicketImportErrorResponse(record.recordNumber(), validationError));
                continue;
            }

            try {
                ticketService.createTicket(ticketRequest);
                successCount++;
            } catch (Exception exception) {
                String errorMessage = "Failed to import ticket: " + rootCauseMessage(exception);
                errors.add(new TicketImportErrorResponse(record.recordNumber(), errorMessage));
            }
        }

        return new TicketImportSummaryResponse(
                records.size(),
                successCount,
                errors.size(),
                List.copyOf(errors)
        );
    }

    private TicketImportParser resolveParser(String filename, String contentType) {
        return ticketImportParsers.stream()
                .filter(parser -> parser.supports(filename, contentType))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unsupported file type. Only CSV, JSON, and XML are supported."
                ));
    }

    private List<ImportRecord> parseRecords(TicketImportParser parser, MultipartFile file) {
        try {
            return parser.parse(file.getBytes());
        } catch (MalformedImportFileException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unable to read import file: " + rootCauseMessage(exception)
            );
        }
    }

    private String validateRecord(TicketRequest ticketRequest) {
        Set<ConstraintViolation<TicketRequest>> violations = validator.validate(ticketRequest);
        if (violations.isEmpty()) {
            return null;
        }

        return violations.stream()
                .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining("; "));
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
