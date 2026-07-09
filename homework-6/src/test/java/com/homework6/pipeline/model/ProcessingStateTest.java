package com.homework6.pipeline.model;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessingStateTest {

    @Test
    void received_setsReceivedStatusAndNullFields() {
        ProcessingState state = ProcessingState.received();

        assertEquals(TransactionStatus.RECEIVED, state.status());
        assertNull(state.reasonCode());
        assertNull(state.reason());
        assertNull(state.riskScore());
        assertTrue(state.riskFactors().isEmpty());
        assertNull(state.settlementId());
        assertNull(state.settledAt());
    }

    @Test
    void validated_setsValidatedStatusAndClearsReason() {
        ProcessingState state = ProcessingState.received().validated();
        assertEquals(TransactionStatus.VALIDATED, state.status());
        assertNull(state.reasonCode());
    }

    @Test
    void rejected_setsReasonCodeAndReason() {
        ProcessingState state = ProcessingState.received().rejected("NEGATIVE_AMOUNT", "amount must be positive");

        assertEquals(TransactionStatus.REJECTED, state.status());
        assertEquals("NEGATIVE_AMOUNT", state.reasonCode());
        assertEquals("amount must be positive", state.reason());
    }

    @Test
    void fraudCleared_carriesRiskScoreAndFactors() {
        ProcessingState state = ProcessingState.received().validated()
                .fraudCleared(40, List.of("unusual_timing", "cross_border"));

        assertEquals(TransactionStatus.FRAUD_CLEARED, state.status());
        assertEquals(40, state.riskScore());
        assertEquals(List.of("unusual_timing", "cross_border"), state.riskFactors());
    }

    @Test
    void flaggedForReview_setsThresholdReasonCodeAndScore() {
        ProcessingState state = ProcessingState.received().validated()
                .flaggedForReview(70, List.of("high_value"));

        assertEquals(TransactionStatus.FLAGGED_FOR_REVIEW, state.status());
        assertEquals("FRAUD_RISK_THRESHOLD_EXCEEDED", state.reasonCode());
        assertEquals(70, state.riskScore());
        assertTrue(state.reason().contains("70"));
    }

    @Test
    void complianceCleared_setsClearedStatus() {
        ProcessingState state = ProcessingState.received().validated().fraudCleared(0, List.of())
                .complianceCleared();
        assertEquals(TransactionStatus.COMPLIANCE_CLEARED, state.status());
    }

    @Test
    void complianceHold_setsReasonCodeAndReason() {
        ProcessingState state = ProcessingState.received().validated().fraudCleared(0, List.of())
                .complianceHold("LARGE_CROSS_BORDER_WIRE", "requires manual review");

        assertEquals(TransactionStatus.COMPLIANCE_HOLD, state.status());
        assertEquals("LARGE_CROSS_BORDER_WIRE", state.reasonCode());
        assertEquals("requires manual review", state.reason());
    }

    @Test
    void settled_setsSettlementIdAndSettledAt() {
        OffsetDateTime settledAt = OffsetDateTime.now(ZoneOffset.UTC);
        ProcessingState state = ProcessingState.received().validated().fraudCleared(0, List.of())
                .complianceCleared().settled("settlement-123", settledAt);

        assertEquals(TransactionStatus.SETTLED, state.status());
        assertEquals("settlement-123", state.settlementId());
        assertEquals(settledAt, state.settledAt());
    }
}
