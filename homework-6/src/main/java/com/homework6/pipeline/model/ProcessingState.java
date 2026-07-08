package com.homework6.pipeline.model;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * The mutable-in-effect (but immutable-in-representation) processing state of a
 * transaction as it advances through the pipeline. Each pipeline stage produces a
 * new {@code ProcessingState} rather than mutating an existing one.
 */
public record ProcessingState(
        TransactionStatus status,
        String reasonCode,
        String reason,
        Integer riskScore,
        List<String> riskFactors,
        String settlementId,
        OffsetDateTime settledAt
) {
    public static ProcessingState received() {
        return new ProcessingState(TransactionStatus.RECEIVED, null, null, null, List.of(), null, null);
    }

    public ProcessingState validated() {
        return new ProcessingState(TransactionStatus.VALIDATED, null, null, riskScore, riskFactors, settlementId, settledAt);
    }

    public ProcessingState rejected(String reasonCode, String reason) {
        return new ProcessingState(TransactionStatus.REJECTED, reasonCode, reason, riskScore, riskFactors, settlementId, settledAt);
    }

    public ProcessingState fraudCleared(int riskScore, List<String> riskFactors) {
        return new ProcessingState(TransactionStatus.FRAUD_CLEARED, null, null, riskScore, riskFactors, settlementId, settledAt);
    }

    public ProcessingState flaggedForReview(int riskScore, List<String> riskFactors) {
        return new ProcessingState(TransactionStatus.FLAGGED_FOR_REVIEW, "FRAUD_RISK_THRESHOLD_EXCEEDED",
                "Risk score " + riskScore + " met or exceeded the fraud review threshold", riskScore, riskFactors,
                settlementId, settledAt);
    }

    public ProcessingState complianceCleared() {
        return new ProcessingState(TransactionStatus.COMPLIANCE_CLEARED, null, null, riskScore, riskFactors, settlementId, settledAt);
    }

    public ProcessingState complianceHold(String reasonCode, String reason) {
        return new ProcessingState(TransactionStatus.COMPLIANCE_HOLD, reasonCode, reason, riskScore, riskFactors, settlementId, settledAt);
    }

    public ProcessingState settled(String settlementId, OffsetDateTime settledAt) {
        return new ProcessingState(TransactionStatus.SETTLED, null, null, riskScore, riskFactors, settlementId, settledAt);
    }
}
