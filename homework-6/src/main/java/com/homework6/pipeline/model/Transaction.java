package com.homework6.pipeline.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Immutable raw transaction data as read from {@code sample-transactions.json}.
 * Never mutated after creation — agents transform {@link ProcessingState}, not this.
 */
public record Transaction(
        String transactionId,
        OffsetDateTime timestamp,
        String sourceAccount,
        String destinationAccount,
        BigDecimal amount,
        String currency,
        String transactionType,
        String description,
        Map<String, String> metadata
) {
    public String country() {
        return metadata == null ? null : metadata.get("country");
    }
}
