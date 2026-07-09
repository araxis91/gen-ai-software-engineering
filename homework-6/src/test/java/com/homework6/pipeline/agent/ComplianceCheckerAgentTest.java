package com.homework6.pipeline.agent;

import com.homework6.pipeline.audit.AuditLogger;
import com.homework6.pipeline.model.PipelineMessage;
import com.homework6.pipeline.model.Transaction;
import com.homework6.pipeline.model.TransactionRecord;
import com.homework6.pipeline.model.TransactionStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComplianceCheckerAgentTest {

    private final ComplianceCheckerAgent agent = new ComplianceCheckerAgent(new AuditLogger());

    private Transaction transaction(String destinationAccount, String amount, String type, String country) {
        return new Transaction("TXN-TEST", OffsetDateTime.parse("2026-03-16T09:00:00Z"), "ACC-1003",
                destinationAccount, new BigDecimal(amount), "USD", type, "test",
                Map.of("channel", "online", "country", country));
    }

    private PipelineMessage process(Transaction transaction) {
        var record = TransactionRecord.received(transaction);
        var fraudCleared = record.withState(record.state().validated().fraudCleared(0, java.util.List.of()));
        PipelineMessage initial = PipelineMessage.initial("fraud_detector", ComplianceCheckerAgent.NAME, fraudCleared);
        return agent.process(initial);
    }

    @Test
    void process_blockedDestinationAccount_rejectsWithBlockedDestinationAccountReasonCode() {
        Transaction tx = transaction("ACC-9999", "9999.99", "transfer", "US");

        PipelineMessage result = process(tx);

        assertEquals(TransactionStatus.REJECTED, result.data().state().status());
        assertEquals("BLOCKED_DESTINATION_ACCOUNT", result.data().state().reasonCode());
    }

    @Test
    void process_largeCrossBorderWireTransfer_putsOnComplianceHold() {
        Transaction tx = transaction("ACC-6600", "75000.00", "wire_transfer", "DE");

        PipelineMessage result = process(tx);

        assertEquals(TransactionStatus.COMPLIANCE_HOLD, result.data().state().status());
        assertEquals("LARGE_CROSS_BORDER_WIRE", result.data().state().reasonCode());
    }

    @Test
    void process_domesticWireTransferAboveThreshold_clearsSinceNotCrossBorder() {
        Transaction tx = transaction("ACC-6600", "75000.00", "wire_transfer", "US");

        PipelineMessage result = process(tx);

        assertEquals(TransactionStatus.COMPLIANCE_CLEARED, result.data().state().status());
        assertEquals("settlement_processor", result.targetAgent());
    }

    @Test
    void process_normalTransfer_clearsAndRoutesToSettlementProcessor() {
        Transaction tx = transaction("ACC-2001", "1500.00", "transfer", "US");

        PipelineMessage result = process(tx);

        assertEquals(TransactionStatus.COMPLIANCE_CLEARED, result.data().state().status());
        assertEquals("settlement_processor", result.targetAgent());
    }

    @Test
    void isBlockedDestination_accountOnDenylist_returnsTrue() {
        assertTrue(agent.isBlockedDestination(transaction("ACC-9999", "100.00", "transfer", "US")));
    }

    @Test
    void isBlockedDestination_accountNotOnDenylist_returnsFalse() {
        assertFalse(agent.isBlockedDestination(transaction("ACC-2001", "100.00", "transfer", "US")));
    }

    @Test
    void requiresManualHold_domesticWire_returnsFalse() {
        assertFalse(agent.requiresManualHold(transaction("ACC-6600", "75000.00", "wire_transfer", "US")));
    }

    @Test
    void requiresManualHold_crossBorderNonWireTransfer_returnsFalse() {
        assertFalse(agent.requiresManualHold(transaction("ACC-6600", "75000.00", "transfer", "DE")));
    }

    @Test
    void requiresManualHold_crossBorderWireBelowThreshold_returnsFalse() {
        assertFalse(agent.requiresManualHold(transaction("ACC-6600", "1000.00", "wire_transfer", "DE")));
    }
}
