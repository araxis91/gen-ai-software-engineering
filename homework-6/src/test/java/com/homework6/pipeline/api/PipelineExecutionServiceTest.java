package com.homework6.pipeline.api;

import com.homework6.pipeline.PipelineExecutor;
import com.homework6.pipeline.PipelineSequence;
import com.homework6.pipeline.PipelineSummaryWriter;
import com.homework6.pipeline.agent.ComplianceCheckerAgent;
import com.homework6.pipeline.agent.FraudDetectorAgent;
import com.homework6.pipeline.agent.PipelineAgent;
import com.homework6.pipeline.agent.SettlementProcessorAgent;
import com.homework6.pipeline.agent.TransactionValidatorAgent;
import com.homework6.pipeline.audit.AuditLogger;
import com.homework6.pipeline.messaging.FileMessageBus;
import com.homework6.pipeline.model.Transaction;
import com.homework6.pipeline.model.TransactionRecord;
import com.homework6.pipeline.model.TransactionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Plain unit test -- no Spring context needed, PipelineExecutionService is a regular POJO. */
class PipelineExecutionServiceTest {

    @TempDir
    Path tempDir;

    private PipelineExecutionService newService() {
        AuditLogger auditLogger = new AuditLogger();
        Map<String, PipelineAgent> agentsByName = Map.of(
                TransactionValidatorAgent.NAME, new TransactionValidatorAgent(auditLogger),
                FraudDetectorAgent.NAME, new FraudDetectorAgent(auditLogger),
                ComplianceCheckerAgent.NAME, new ComplianceCheckerAgent(auditLogger),
                SettlementProcessorAgent.NAME, new SettlementProcessorAgent(auditLogger));
        FileMessageBus bus = new FileMessageBus();

        return new PipelineExecutionService(bus, new PipelineExecutor(), new PipelineSummaryWriter(bus),
                PipelineSequence.defaultSequence(), agentsByName, auditLogger, tempDir.resolve("shared").toString());
    }

    private Transaction transaction(String id, String amount, String currency) {
        return new Transaction(id, OffsetDateTime.parse("2026-03-16T09:00:00Z"), "ACC-1001", "ACC-2001",
                new BigDecimal(amount), currency, "transfer", "test", Map.of("channel", "online", "country", "US"));
    }

    @Test
    void constructor_createsSharedDirectoriesUpFront() {
        newService();
        assertTrue(Files.isDirectory(tempDir.resolve("shared").resolve("results")));
        assertTrue(Files.isDirectory(tempDir.resolve("shared").resolve("input")));
    }

    @Test
    void submit_newTransaction_runsToCompletionAndPersists() {
        PipelineExecutionService service = newService();

        TransactionRecord result = service.submit(transaction("TXN001", "1500.00", "USD"));

        assertEquals(TransactionStatus.SETTLED, result.state().status());
        assertTrue(service.findById("TXN001").isPresent());
    }

    @Test
    void submit_sameTransactionIdTwice_secondCallReturnsExistingResultUnchanged() {
        PipelineExecutionService service = newService();

        TransactionRecord first = service.submit(transaction("TXN001", "1500.00", "USD"));
        TransactionRecord second = service.submit(transaction("TXN001", "1500.00", "USD"));

        assertEquals(first.state().settlementId(), second.state().settlementId(),
                "must not generate a new settlement id on the second submission");
    }

    @Test
    void findById_unknownTransaction_returnsEmpty() {
        assertEquals(Optional.empty(), newService().findById("TXN999"));
    }

    @Test
    void listAll_afterTwoSubmissions_returnsBoth() {
        PipelineExecutionService service = newService();
        service.submit(transaction("TXN001", "1500.00", "USD"));
        service.submit(transaction("TXN002", "200.00", "ZZZ"));

        assertEquals(2, service.listAll().size());
    }

    @Test
    void latestSummary_beforeAnySubmission_returnsEmpty() {
        assertEquals(Optional.empty(), newService().latestSummary());
    }

    @Test
    void latestSummary_afterSubmission_reflectsIt() {
        PipelineExecutionService service = newService();
        service.submit(transaction("TXN001", "1500.00", "USD"));

        var summary = service.latestSummary();
        assertTrue(summary.isPresent());
        assertEquals(1L, summary.get().countsByStatus().get("SETTLED"));
    }
}
