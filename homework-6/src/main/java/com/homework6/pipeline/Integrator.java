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
 * Orchestrates the banking pipeline over shared/{input,processing,output,results}.
 *
 * <p>The pipeline's stage order is entirely governed by a {@link PipelineSequence} —
 * agents themselves never decide what runs after them (see {@code PipelineAgent}
 * javadoc). This means the stages can be reconfigured to run in any order, as a subset,
 * or one at a time via {@link #runStage(String)}, without touching any agent class.
 *
 * <p>Re-running is safe: transactions already settled/rejected/flagged/held are skipped.
 */
public final class Integrator {

    private static final Logger log = LoggerFactory.getLogger(Integrator.class);

    private final Path sharedRoot;
    private final Path input;
    private final Path processing;
    private final Path output;
    private final Path results;
    private final Path sampleTransactionsFile;
    private final PipelineSequence sequence;

    private final FileMessageBus bus = new FileMessageBus();
    private final AuditLogger auditLogger = new AuditLogger();
    private final Map<String, PipelineAgent> agentsByName;

    public Integrator() {
        this(Path.of("shared"), Path.of("sample-transactions.json"));
    }

    public Integrator(Path sharedRoot, Path sampleTransactionsFile) {
        this(sharedRoot, sampleTransactionsFile, PipelineSequence.defaultSequence());
    }

    public Integrator(Path sharedRoot, Path sampleTransactionsFile, PipelineSequence sequence) {
        this.sharedRoot = sharedRoot;
        this.input = sharedRoot.resolve("input");
        this.processing = sharedRoot.resolve("processing");
        this.output = sharedRoot.resolve("output");
        this.results = sharedRoot.resolve("results");
        this.sampleTransactionsFile = sampleTransactionsFile;

        TransactionValidatorAgent validator = new TransactionValidatorAgent(auditLogger);
        FraudDetectorAgent fraudDetector = new FraudDetectorAgent(auditLogger);
        ComplianceCheckerAgent complianceChecker = new ComplianceCheckerAgent(auditLogger);
        SettlementProcessorAgent settlementProcessor = new SettlementProcessorAgent(auditLogger);
        this.agentsByName = Map.of(
                validator.name(), validator,
                fraudDetector.name(), fraudDetector,
                complianceChecker.name(), complianceChecker,
                settlementProcessor.name(), settlementProcessor);

        for (String agentName : sequence.agentNames()) {
            if (!agentsByName.containsKey(agentName)) {
                throw new IllegalArgumentException("Unknown pipeline agent '" + agentName
                        + "'. Known agents: " + agentsByName.keySet());
            }
        }
        this.sequence = sequence;
    }

    public static void main(String[] args) {
        try {
            CliArgs cliArgs = CliArgs.parse(args);
            Integrator integrator = new Integrator(Path.of("shared"), cliArgs.sampleTransactionsFile(), cliArgs.sequence());
            if (cliArgs.singleStage() != null) {
                integrator.runStage(cliArgs.singleStage());
            } else {
                integrator.run();
            }
        } catch (CliArgs.HelpRequested e) {
            System.out.println(CliArgs.USAGE);
        } catch (RuntimeException e) {
            log.error("Pipeline run failed", e);
            System.exit(1);
        }
    }

    /** Seeds shared/input/ from {@code sampleTransactionsFile}, then runs every stage in {@link #sequence} order. */
    public void run() {
        List<Transaction> transactions = loadTransactions();
        log.info("Loaded {} transactions from {}", transactions.size(), sampleTransactionsFile);
        bus.ensureDirectories(input, processing, output, results);
        int skipped = enqueueInitialMessages(transactions);

        for (String agentName : sequence.agentNames()) {
            runStage(agentName);
        }

        Map<String, Long> summary = writeSummary(transactions.size());
        log.info("Pipeline run complete. {} already-settled transactions skipped. Status counts: {}", skipped, summary);
    }

    /**
     * Loads {@code sampleTransactionsFile} and writes one initial message per (not yet
     * terminal) transaction into {@code shared/input/}, targeting {@link PipelineSequence#first()}.
     * Exposed separately from {@link #run()} so callers can seed once and then drive
     * {@link #runStage(String)} themselves, one call at a time, in whatever order they choose.
     */
    public int seedInput() {
        List<Transaction> transactions = loadTransactions();
        log.info("Loaded {} transactions from {}", transactions.size(), sampleTransactionsFile);
        bus.ensureDirectories(input, processing, output, results);
        return enqueueInitialMessages(transactions);
    }

    /**
     * Runs exactly one named stage over whatever's currently queued for it, and routes
     * its output per {@link #sequence}. Reads from {@code shared/input/} if {@code agentName}
     * is the sequence's first stage, otherwise from {@code shared/output/} filtered by
     * {@code target_agent == agentName}. Can be called on its own, any number of times,
     * in any order — each call only processes whatever has actually been routed to it,
     * so calling stages "out of order" relative to a full run is harmless (it just finds
     * nothing queued yet for a stage nothing has reached).
     */
    public void runStage(String agentName) {
        bus.ensureDirectories(input, processing, output, results);

        PipelineAgent agent = agentsByName.get(agentName);
        if (agent == null) {
            throw new IllegalArgumentException("Unknown pipeline agent '" + agentName
                    + "'. Known agents: " + agentsByName.keySet());
        }

        Path sourceDir = agentName.equals(sequence.first()) ? input : output;
        List<PipelineMessage> queued = bus.listByTargetAgent(sourceDir, agentName);

        log.info("Stage [{}]: processing {} message(s) from {}", agentName, queued.size(), sourceDir);

        String nextAgent = sequence.nextAfter(agentName).orElse(null);

        for (PipelineMessage message : queued) {
            String transactionId = message.data().transactionId();
            bus.moveRaw(sourceDir, processing, transactionId);

            TransactionRecord updated = agent.process(message.data());
            boolean terminal = updated.state().status().isTerminal() || nextAgent == null;

            PipelineMessage result = terminal
                    ? message.terminal(agentName, updated)
                    : message.routedTo(agentName, nextAgent, updated);

            bus.write(terminal ? results : output, result);
            bus.delete(processing, transactionId);
        }
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
            PipelineMessage message = PipelineMessage.initial("integrator", sequence.first(), record);
            bus.write(input, message);
        }
        return skipped;
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
                sequence.agentNames(),
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
            List<String> agentSequence,
            Map<String, Long> countsByStatus,
            List<TransactionOutcomeSummary> outcomes) {
    }

    private record TransactionOutcomeSummary(String transactionId, String status, String reasonCode, Integer riskScore) {
    }
}
