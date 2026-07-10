package com.homework6.pipeline.model;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * The envelope agents pass through {@code shared/} directories, matching the standard
 * message format in TASKS.md: message metadata (id, timestamp, routing) wraps the
 * {@link TransactionRecord} payload in {@code data}.
 */
public record PipelineMessage(
        String messageId,
        OffsetDateTime timestamp,
        String sourceAgent,
        String targetAgent,
        String messageType,
        TransactionRecord data
) {
    public static PipelineMessage initial(String sourceAgent, String targetAgent, TransactionRecord data) {
        return new PipelineMessage(UUID.randomUUID().toString(), OffsetDateTime.now(ZoneOffset.UTC),
                sourceAgent, targetAgent, "transaction", data);
    }

    public PipelineMessage routedTo(String sourceAgent, String targetAgent, TransactionRecord newData) {
        return new PipelineMessage(UUID.randomUUID().toString(), OffsetDateTime.now(ZoneOffset.UTC),
                sourceAgent, targetAgent, messageType, newData);
    }

    public PipelineMessage terminal(String sourceAgent, TransactionRecord newData) {
        return new PipelineMessage(UUID.randomUUID().toString(), OffsetDateTime.now(ZoneOffset.UTC),
                sourceAgent, null, messageType, newData);
    }
}
