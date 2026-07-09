package com.homework6.pipeline.api.dto;

import com.homework6.pipeline.model.TransactionRecord;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "The current terminal (or in-progress) result for one transaction")
public record TransactionResultResponse(
        @Schema(example = "TXN001") String transactionId,
        @Schema(example = "SETTLED") String status,
        @Schema(example = "INVALID_CURRENCY") String reasonCode,
        String reason,
        @Schema(example = "70") Integer riskScore,
        List<String> riskFactors,
        String settlementId,
        OffsetDateTime settledAt
) {
    public static TransactionResultResponse from(TransactionRecord record) {
        var state = record.state();
        return new TransactionResultResponse(
                record.transactionId(),
                state.status().name(),
                state.reasonCode(),
                state.reason(),
                state.riskScore(),
                state.riskFactors(),
                state.settlementId(),
                state.settledAt());
    }
}
