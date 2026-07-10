package com.homework6.pipeline.model;

/**
 * All statuses a {@link TransactionRecord} can hold as it moves through the pipeline.
 * SETTLED, REJECTED, FLAGGED_FOR_REVIEW, and COMPLIANCE_HOLD are terminal — a message
 * carrying one of these must be written to {@code shared/results/} and never re-queued.
 */
public enum TransactionStatus {
    RECEIVED,
    VALIDATED,
    REJECTED,
    FRAUD_CLEARED,
    FLAGGED_FOR_REVIEW,
    COMPLIANCE_CLEARED,
    COMPLIANCE_HOLD,
    SETTLED;

    public boolean isTerminal() {
        return this == REJECTED
                || this == FLAGGED_FOR_REVIEW
                || this == COMPLIANCE_HOLD
                || this == SETTLED;
    }
}
