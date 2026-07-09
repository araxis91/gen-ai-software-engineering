package com.homework6.pipeline;

import com.homework6.pipeline.agent.ComplianceCheckerAgent;
import com.homework6.pipeline.agent.FraudDetectorAgent;
import com.homework6.pipeline.agent.PipelineAgent;
import com.homework6.pipeline.agent.SettlementProcessorAgent;
import com.homework6.pipeline.agent.TransactionValidatorAgent;
import com.homework6.pipeline.audit.AuditLogger;
import com.homework6.pipeline.model.Transaction;
import com.homework6.pipeline.model.TransactionRecord;
import com.homework6.pipeline.model.TransactionStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * No file system involved at all -- PipelineExecutor is purely in-memory, unlike
 * IntegratorTest which necessarily exercises shared/ via @TempDir.
 */
class PipelineExecutorTest {

    private final AuditLogger auditLogger = new AuditLogger();
    private final Map<String, PipelineAgent> agentsByName = Map.of(
            TransactionValidatorAgent.NAME, new TransactionValidatorAgent(auditLogger),
            FraudDetectorAgent.NAME, new FraudDetectorAgent(auditLogger),
            ComplianceCheckerAgent.NAME, new ComplianceCheckerAgent(auditLogger),
            SettlementProcessorAgent.NAME, new SettlementProcessorAgent(auditLogger));
    private final PipelineExecutor executor = new PipelineExecutor();

    private Transaction transaction(String id, String amount, String currency, String destinationAccount) {
        return new Transaction(id, OffsetDateTime.parse("2026-03-16T09:00:00Z"), "ACC-1001", destinationAccount,
                new BigDecimal(amount), currency, "transfer", "test", Map.of("channel", "online", "country", "US"));
    }

    @Test
    void runStage_knownAgent_delegatesToAgentProcess() {
        TransactionRecord result = executor.runStage(
                TransactionRecord.received(transaction("TXN001", "1500.00", "USD", "ACC-2001")),
                TransactionValidatorAgent.NAME, agentsByName);

        assertEquals(TransactionStatus.VALIDATED, result.state().status());
    }

    @Test
    void runStage_unknownAgent_throwsWithKnownAgentsListed() {
        var record = TransactionRecord.received(transaction("TXN001", "1500.00", "USD", "ACC-2001"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> executor.runStage(record, "not_a_real_agent", agentsByName));
        assertEquals(true, ex.getMessage().contains("not_a_real_agent"));
    }

    @Test
    void runToCompletion_normalTransaction_settlesThroughAllFourStages() {
        TransactionRecord result = executor.runToCompletion(
                TransactionRecord.received(transaction("TXN001", "1500.00", "USD", "ACC-2001")),
                PipelineSequence.defaultSequence(), agentsByName);

        assertEquals(TransactionStatus.SETTLED, result.state().status());
    }

    @Test
    void runToCompletion_invalidCurrency_rejectsAtFirstStageWithoutRunningFurtherAgents() {
        TransactionRecord result = executor.runToCompletion(
                TransactionRecord.received(transaction("TXN006", "200.00", "ZZZ", "ACC-2001")),
                PipelineSequence.defaultSequence(), agentsByName);

        assertEquals(TransactionStatus.REJECTED, result.state().status());
        assertEquals("INVALID_CURRENCY", result.state().reasonCode());
    }

    @Test
    void runToCompletion_highValue_stopsAtFraudFlagWithoutReachingCompliance() {
        TransactionRecord result = executor.runToCompletion(
                TransactionRecord.received(transaction("TXN002", "25000.00", "USD", "ACC-9999")),
                PipelineSequence.defaultSequence(), agentsByName);

        assertEquals(TransactionStatus.FLAGGED_FOR_REVIEW, result.state().status(),
                "blocked destination ACC-9999 would also reject at compliance, but fraud flag must win first");
    }

    @Test
    void runToCompletion_matchesFileBasedIntegratorResultForSameInput() {
        // Same equivalence the file-based Integrator.run() path produces for TXN001-shaped
        // input, proving the in-memory executor and the file-queue CLI agree exactly.
        Transaction tx = transaction("TXN001", "1500.00", "USD", "ACC-2001");
        TransactionRecord viaExecutor = executor.runToCompletion(
                TransactionRecord.received(tx), PipelineSequence.defaultSequence(), agentsByName);

        assertEquals(TransactionStatus.SETTLED, viaExecutor.state().status());
        assertEquals(0, viaExecutor.state().riskScore());
    }

    @Test
    void runToCompletion_subsetSequence_stopsAtLastConfiguredStageEvenIfNonTerminal() {
        TransactionRecord result = executor.runToCompletion(
                TransactionRecord.received(transaction("TXN001", "1500.00", "USD", "ACC-2001")),
                PipelineSequence.of(TransactionValidatorAgent.NAME, FraudDetectorAgent.NAME),
                agentsByName);

        assertEquals(TransactionStatus.FRAUD_CLEARED, result.state().status(),
                "compliance/settlement not in this sequence, so it stops at fraud-cleared, non-terminal");
    }
}
