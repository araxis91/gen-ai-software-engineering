package com.homework6.pipeline.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** Mirrors {@code shared/results/pipeline-summary.json} exactly (same field shape). */
@Schema(description = "The latest pipeline-summary.json content")
public record PipelineSummaryResponse(
        OffsetDateTime generatedAt,
        int totalTransactions,
        int resultsWritten,
        List<String> agentSequence,
        Map<String, Long> countsByStatus,
        List<TransactionOutcomeSummary> outcomes
) {
    public record TransactionOutcomeSummary(String transactionId, String status, String reasonCode, Integer riskScore) {
    }
}
