package com.homework6.pipeline.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.homework6.pipeline.messaging.JsonMapper;
import com.homework6.pipeline.model.ProcessingState;
import com.homework6.pipeline.model.Transaction;
import com.homework6.pipeline.model.TransactionRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-layer test: verifies status codes, JSON shapes, and error mapping through
 * GlobalExceptionHandler, with PipelineExecutionService mocked out (that class already
 * has its own dedicated unit test).
 */
// PipelineBeansConfig is imported so this MVC slice uses the project's snake_case,
// JavaTimeModule-registered ObjectMapper (see PipelineBeansConfig.objectMapper()) for
// both request deserialization and response serialization -- without it, Spring Boot's
// default camelCase auto-configured ObjectMapper would apply instead, breaking every
// "transaction_id"-shaped JSON path used below.
@WebMvcTest(TransactionController.class)
@Import({GlobalExceptionHandler.class, PipelineBeansConfig.class})
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PipelineExecutionService service;

    private final ObjectMapper objectMapper = JsonMapper.instance();

    private Transaction transaction() {
        return new Transaction("TXN001", OffsetDateTime.parse("2026-03-16T09:00:00Z"), "ACC-1001", "ACC-2001",
                new BigDecimal("1500.00"), "USD", "transfer", "test", Map.of("channel", "online", "country", "US"));
    }

    private TransactionRecord settledRecord() {
        return TransactionRecord.received(transaction()).withState(
                ProcessingState.received().validated().fraudCleared(0, List.of()).complianceCleared()
                        .settled("settlement-1", OffsetDateTime.parse("2026-03-16T09:00:01Z")));
    }

    @Test
    void submit_validRequest_returns200WithTerminalResult() throws Exception {
        when(service.submit(any())).thenReturn(settledRecord());

        String body = objectMapper.writeValueAsString(Map.of(
                "transaction_id", "TXN001",
                "timestamp", "2026-03-16T09:00:00Z",
                "source_account", "ACC-1001",
                "destination_account", "ACC-2001",
                "amount", "1500.00",
                "currency", "USD",
                "transaction_type", "transfer"));

        mockMvc.perform(post("/api/v1/transactions").contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transaction_id").value("TXN001"))
                .andExpect(jsonPath("$.status").value("SETTLED"));
    }

    @Test
    void submit_missingRequiredField_returns400ValidationError() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "transaction_id", "",
                "timestamp", "2026-03-16T09:00:00Z",
                "source_account", "ACC-1001",
                "destination_account", "ACC-2001",
                "amount", "1500.00",
                "currency", "USD",
                "transaction_type", "transfer"));

        mockMvc.perform(post("/api/v1/transactions").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void submit_malformedJson_returns400MalformedRequest() throws Exception {
        mockMvc.perform(post("/api/v1/transactions").contentType("application/json").content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void getById_existingTransaction_returns200() throws Exception {
        when(service.findById("TXN001")).thenReturn(Optional.of(settledRecord()));

        mockMvc.perform(get("/api/v1/transactions/TXN001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"));
    }

    @Test
    void getById_unknownTransaction_returns404() throws Exception {
        when(service.findById("TXN999")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/transactions/TXN999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSACTION_NOT_FOUND"));
    }

    @Test
    void listAll_noTransactionsYet_returnsEmptyArrayNot404() throws Exception {
        when(service.listAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void listAll_withResults_returnsAllOfThem() throws Exception {
        when(service.listAll()).thenReturn(List.of(settledRecord()));

        mockMvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].transaction_id").value("TXN001"));
    }

    @Test
    void getSummary_noneAvailableYet_returns404() throws Exception {
        when(service.latestSummary()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/pipeline/summary"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSACTION_NOT_FOUND"));
    }

    @Test
    void getSummary_available_returns200WithFields() throws Exception {
        var summary = new com.homework6.pipeline.PipelineSummaryWriter.PipelineSummary(
                OffsetDateTime.parse("2026-03-16T09:00:00Z"), 1, 1,
                List.of("transaction_validator"), Map.of("SETTLED", 1L),
                List.of(new com.homework6.pipeline.PipelineSummaryWriter.TransactionOutcomeSummary(
                        "TXN001", "SETTLED", null, 0)));
        when(service.latestSummary()).thenReturn(Optional.of(summary));

        mockMvc.perform(get("/api/v1/pipeline/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_transactions").value(1))
                .andExpect(jsonPath("$.counts_by_status.SETTLED").value(1));
    }

    @Test
    void submit_delegatesConvertedTransactionToService() throws Exception {
        when(service.submit(any())).thenReturn(settledRecord());

        String body = objectMapper.writeValueAsString(Map.of(
                "transaction_id", "TXN001",
                "timestamp", "2026-03-16T09:00:00Z",
                "source_account", "ACC-1001",
                "destination_account", "ACC-2001",
                "amount", "1500.00",
                "currency", "USD",
                "transaction_type", "transfer"));

        mockMvc.perform(post("/api/v1/transactions").contentType("application/json").content(body))
                .andExpect(status().isOk());

        verify(service).submit(any());
    }
}
