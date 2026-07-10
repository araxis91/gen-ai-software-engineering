package com.homework6.pipeline.agent;

import com.homework6.pipeline.audit.AuditLogger;
import com.homework6.pipeline.model.PipelineMessage;
import com.homework6.pipeline.model.Transaction;
import com.homework6.pipeline.model.TransactionRecord;
import com.homework6.pipeline.model.TransactionStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SettlementProcessorAgentTest {

    private final SettlementProcessorAgent agent = new SettlementProcessorAgent(new AuditLogger());

    @Test
    void process_complianceClearedTransaction_settlesWithGeneratedSettlementId() {
        Transaction transaction = new Transaction("TXN001", OffsetDateTime.parse("2026-03-16T09:00:00Z"),
                "ACC-1001", "ACC-2001", new BigDecimal("1500.00"), "USD", "transfer", "Monthly rent payment",
                Map.of("channel", "online", "country", "US"));
        var record = TransactionRecord.received(transaction);
        var complianceCleared = record.withState(
                record.state().validated().fraudCleared(0, List.of()).complianceCleared());
        PipelineMessage initial = PipelineMessage.initial("compliance_checker", SettlementProcessorAgent.NAME,
                complianceCleared);

        PipelineMessage result = agent.process(initial);

        assertEquals(TransactionStatus.SETTLED, result.data().state().status());
        assertNotNull(result.data().state().settlementId());
        assertNotNull(result.data().state().settledAt());
        assertNull(result.targetAgent(), "settlement is terminal -- must not route onward");
    }

    @Test
    void name_returnsSettlementProcessorConstant() {
        assertEquals("settlement_processor", agent.name());
    }
}
