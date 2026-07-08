package com.homework6.pipeline;

import com.homework6.pipeline.agent.ComplianceCheckerAgent;
import com.homework6.pipeline.agent.FraudDetectorAgent;
import com.homework6.pipeline.agent.PipelineAgent;
import com.homework6.pipeline.agent.SettlementProcessorAgent;
import com.homework6.pipeline.agent.TransactionValidatorAgent;
import com.homework6.pipeline.audit.AuditLogger;
import com.homework6.pipeline.messaging.FileMessageBus;
import com.homework6.pipeline.messaging.JsonMapper;
import com.homework6.pipeline.model.PipelineMessage;
import com.homework6.pipeline.model.Transaction;
import com.homework6.pipeline.model.TransactionRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates the four-stage banking pipeline over shared/{input,processing,output,results}.
 * Re-running is safe: transactions already settled/rejected/flagged/held are skipped.
 */
public final class Integrator {

    private static final Logger log = LoggerFactory.getLogger(Integrator.class);

    private final Path sharedRoot;
    private final Path input;
    private final Path processing;
    private final Path output;
    private final Path results;
    private final Path sampleTransactionsFile;

    private final FileMessageBus bus = new FileMessageBus();
    private final AuditLogger auditLogger = new AuditLogger();

    private final TransactionValidatorAgent validator = new TransactionValidatorAgent(auditLogger);
    private final FraudDetectorAgent fraudDetector = new FraudDetectorAgent(auditLogger);
    private final ComplianceCheckerAgent complianceChecker = new ComplianceCheckerAgent(auditLogger);
    private final SettlementProcessorAgent settlementProcessor = new SettlementProcessorAgent(auditLogger);

    public Integrator() {
        this(Path.of("shared"), Path.of("sample-transactions.json"));
    }

    public Integrator(Path sharedRoot, Path sampleTransactionsFile) {
        this.sharedRoot = sharedRoot;
        this.input = sharedRoot.resolve("input");
        this.processing = sharedRoot.resolve("processing");
        this.output = sharedRoot.resolve("output");
        this.results = sharedRoot.resolve("results");
        this.sampleTransactionsFile = sampleTransactionsFile;
    }

    public static void main(String[] args) {
        try {
            new Integrator().run();
        } catch (RuntimeException e) {
            log.error("Pipeline run failed", e);
            System.exit(1);
        }
    }

    public void run() {
        bus.ensureDirectories(input, processing, output, results);

        List<Transaction> transactions = loadTransactions();
        log.info("Loaded {} transactions from {}", transactions.size(), sampleTransactionsFile);

        int skipped = enqueueInitialMessages(transactions);

        runStage(validator, input, null);
        runStage(fraudDetector, output, FraudDetectorAgent.NAME);
        runStage(complianceChecker, output, ComplianceCheckerAgent.NAME);
        runStage(settlementProcessor, output, SettlementProcessorAgent.NAME);

        Map<String, Long> summary = writeSummary(transactions.size());
        log.info("Pipeline run complete. {} already-settled transactions skipped. Status counts: {}", skipped, summary);
    }

    private List<Transaction> loadTransactions() {
        try {
            Transaction[] array = JsonMapper.instance().readValue(sampleTransactionsFile.toFile(), Transaction[].class);
            return List.of(array);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load " + sampleTransactionsFile, e);
        }
    }

    /** Writes an initial message per transaction into shared/input/, skipping any already terminal in shared/results/. */
    private int enqueueInitialMessages(List<Transaction> transactions) {
        int skipped = 0;
        for (Transaction tx : transactions) {
            if (bus.exists(results, tx.transactionId())) {
                auditLogger.record("integrator", tx.transactionId(), "SKIPPED_DUPLICATE");
                skipped++;
                continue;
            }
            TransactionRecord record = TransactionRecord.received(tx);
            PipelineMessage message = PipelineMessage.initial("integrator", TransactionValidatorAgent.NAME, record);
            bus.write(input, message);
        }
        return skipped;
    }

    /**
     * Runs one pipeline stage over every queued message in {@code sourceDir} that targets
     * {@code agent} (or every message, when {@code targetFilter} is null — used for the
     * first stage, where everything in shared/input/ is already targeted at the validator).
     */
    private void runStage(PipelineAgent agent, Path sourceDir, String targetFilter) {
        List<PipelineMessage> queued = targetFilter == null
                ? bus.listMessages(sourceDir)
                : bus.listByTargetAgent(sourceDir, targetFilter);

        log.info("Stage [{}]: processing {} message(s) from {}", agent.name(), queued.size(), sourceDir);

        for (PipelineMessage message : queued) {
            String transactionId = message.data().transactionId();
            bus.moveRaw(sourceDir, processing, transactionId);

            PipelineMessage result = agent.process(message);

            if (result.data().state().status().isTerminal()) {
                bus.write(results, result);
            } else {
                bus.write(output, result);
            }
            bus.delete(processing, transactionId);
        }
    }

    private Map<String, Long> writeSummary(int totalTransactions) {
        List<PipelineMessage> outcomes = bus.listMessages(results);

        Map<String, Long> countsByStatus = new LinkedHashMap<>();
        for (PipelineMessage outcome : outcomes) {
            String status = outcome.data().state().status().name();
            countsByStatus.merge(status, 1L, Long::sum);
        }

        PipelineSummary summary = new PipelineSummary(
                OffsetDateTime.now(ZoneOffset.UTC),
                totalTransactions,
                outcomes.size(),
                countsByStatus,
                outcomes.stream().map(this::toOutcomeSummary).toList());

        bus.writeJson(results.resolve("pipeline-summary.json"), summary);
        return countsByStatus;
    }

    private TransactionOutcomeSummary toOutcomeSummary(PipelineMessage message) {
        var record = message.data();
        return new TransactionOutcomeSummary(
                record.transactionId(),
                record.state().status().name(),
                record.state().reasonCode(),
                record.state().riskScore());
    }

    private record PipelineSummary(
            OffsetDateTime generatedAt,
            int totalTransactions,
            int resultsWritten,
            Map<String, Long> countsByStatus,
            List<TransactionOutcomeSummary> outcomes) {
    }

    private record TransactionOutcomeSummary(String transactionId, String status, String reasonCode, Integer riskScore) {
    }
}
