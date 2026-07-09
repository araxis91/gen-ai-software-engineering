package com.homework6.pipeline.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Full-stack test: real embedded server, real HTTP calls, real (but @TempDir-isolated)
 * shared/ directory -- proves the whole wiring (controller -> service -> executor ->
 * agents -> file persistence) works end to end, not just each layer in isolation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PipelineApiIntegrationTest {

    @TempDir
    static Path sharedDir;

    @DynamicPropertySource
    static void overrideSharedDir(DynamicPropertyRegistry registry) {
        registry.add("pipeline.shared-dir", () -> sharedDir.toString());
    }

    @Autowired
    private TestRestTemplate restTemplate;

    private Map<String, Object> requestBody(String transactionId, String amount, String currency) {
        return Map.of(
                "transaction_id", transactionId,
                "timestamp", "2026-03-16T09:00:00Z",
                "source_account", "ACC-1001",
                "destination_account", "ACC-2001",
                "amount", amount,
                "currency", currency,
                "transaction_type", "transfer");
    }

    @Test
    void fullLifecycle_submitThenGetByIdThenListThenSummary() {
        ResponseEntity<Map> submitResponse = restTemplate.postForEntity(
                "/api/v1/transactions", requestBody("ITX001", "1500.00", "USD"), Map.class);
        assertEquals(HttpStatus.OK, submitResponse.getStatusCode());
        assertEquals("SETTLED", submitResponse.getBody().get("status"));

        ResponseEntity<Map> getResponse = restTemplate.getForEntity("/api/v1/transactions/ITX001", Map.class);
        assertEquals(HttpStatus.OK, getResponse.getStatusCode());
        assertEquals("ITX001", getResponse.getBody().get("transaction_id"));

        ResponseEntity<List> listResponse = restTemplate.getForEntity("/api/v1/transactions", List.class);
        assertEquals(HttpStatus.OK, listResponse.getStatusCode());
        assertTrue(listResponse.getBody().size() >= 1);

        ResponseEntity<Map> summaryResponse = restTemplate.getForEntity("/api/v1/pipeline/summary", Map.class);
        assertEquals(HttpStatus.OK, summaryResponse.getStatusCode());
        assertTrue(((Number) summaryResponse.getBody().get("results_written")).intValue() >= 1);
    }

    @Test
    void submitRejectingTransaction_returns200WithRejectedStatus_notAnHttpError() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/transactions", requestBody("ITX002", "200.00", "ZZZ"), Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("REJECTED", response.getBody().get("status"));
        assertEquals("INVALID_CURRENCY", response.getBody().get("reason_code"));
    }

    @Test
    void submittingSameTransactionTwice_secondResponseMatchesFirst() {
        ResponseEntity<Map> first = restTemplate.postForEntity(
                "/api/v1/transactions", requestBody("ITX003", "500.00", "USD"), Map.class);
        ResponseEntity<Map> second = restTemplate.postForEntity(
                "/api/v1/transactions", requestBody("ITX003", "500.00", "USD"), Map.class);

        assertEquals(first.getBody().get("settlement_id"), second.getBody().get("settlement_id"));
    }

    @Test
    void getUnknownTransaction_returns404WithErrorBody() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/api/v1/transactions/UNKNOWN", Map.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("TRANSACTION_NOT_FOUND", response.getBody().get("code"));
    }

    @Test
    void openApiDocs_areReachable() {
        ResponseEntity<String> apiDocs = restTemplate.getForEntity("/v3/api-docs", String.class);
        assertEquals(HttpStatus.OK, apiDocs.getStatusCode());
        assertTrue(apiDocs.getBody().contains("/api/v1/transactions"));
    }
}
