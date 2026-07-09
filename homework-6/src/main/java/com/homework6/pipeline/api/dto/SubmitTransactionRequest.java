package com.homework6.pipeline.api.dto;

import com.homework6.pipeline.model.Transaction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Structural validation only ({@code @NotBlank}/{@code @NotNull} -- a field is present).
 * Business rules (amount must be positive, currency must be a valid ISO 4217 code) stay
 * inside {@code TransactionValidatorAgent}, not duplicated here as bean-validation
 * annotations (see specification-capstone.md Task 2 Implementation Notes).
 */
@Schema(description = "A single transaction to submit to the pipeline")
public record SubmitTransactionRequest(
        @NotBlank(message = "transactionId is required")
        @Schema(example = "TXN001")
        String transactionId,

        @NotNull(message = "timestamp is required")
        @Schema(example = "2026-03-16T09:00:00Z")
        OffsetDateTime timestamp,

        @NotBlank(message = "sourceAccount is required")
        @Schema(example = "ACC-1001")
        String sourceAccount,

        @NotBlank(message = "destinationAccount is required")
        @Schema(example = "ACC-2001")
        String destinationAccount,

        @NotNull(message = "amount is required")
        @Schema(example = "1500.00")
        BigDecimal amount,

        @NotBlank(message = "currency is required")
        @Schema(example = "USD")
        String currency,

        @NotBlank(message = "transactionType is required")
        @Schema(example = "transfer")
        String transactionType,

        @Schema(example = "Monthly rent payment")
        String description,

        @Schema(example = "{\"channel\": \"online\", \"country\": \"US\"}")
        Map<String, String> metadata
) {
    public Transaction toTransaction() {
        return new Transaction(transactionId, timestamp, sourceAccount, destinationAccount, amount, currency,
                transactionType, description, metadata == null ? Map.of() : metadata);
    }
}
