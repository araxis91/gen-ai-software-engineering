package com.homework6.pipeline.api;

import com.homework6.pipeline.PipelineExecutor;
import com.homework6.pipeline.PipelineSequence;
import com.homework6.pipeline.PipelineSummaryWriter;
import com.homework6.pipeline.agent.PipelineAgent;
import com.homework6.pipeline.audit.AuditLogger;
import com.homework6.pipeline.messaging.FileMessageBus;
import com.homework6.pipeline.messaging.JsonMapper;
import com.homework6.pipeline.model.PipelineMessage;
import com.homework6.pipeline.model.Transaction;
import com.homework6.pipeline.model.TransactionRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The single place that checks idempotency, runs {@link PipelineExecutor} synchronously
 * to completion, and persists the result -- {@code TransactionController} stays a thin
 * HTTP-mapping layer, matching the Service Layer pattern used elsewhere in this repo.
 *
 * <p>{@code shared/results/} is shared with the CLI ({@code Integrator}) and the MCP
 * server ({@code mcp/server.py}): a transaction submitted via {@code curl} and one
 * submitted via {@code Integrator.run()} are indistinguishable once terminal.
 */
@Service
public class PipelineExecutionService {

    private static final String SOURCE_AGENT = "pipeline_api";

    private final FileMessageBus bus;
    private final PipelineExecutor executor;
    private final PipelineSummaryWriter summaryWriter;
    private final PipelineSequence sequence;
    private final Map<String, PipelineAgent> agentsByName;
    private final AuditLogger auditLogger;
    private final Path resultsDir;

    public PipelineExecutionService(FileMessageBus bus,
                                     PipelineExecutor executor,
                                     PipelineSummaryWriter summaryWriter,
                                     PipelineSequence sequence,
                                     @Qualifier("agentsByName") Map<String, PipelineAgent> agentsByName,
                                     AuditLogger auditLogger,
                                     @Value("${pipeline.shared-dir:shared}") String sharedDir) {
        this.bus = bus;
        this.executor = executor;
        this.summaryWriter = summaryWriter;
        this.sequence = sequence;
        this.agentsByName = agentsByName;
        this.auditLogger = auditLogger;

        Path sharedRoot = Path.of(sharedDir);
        this.resultsDir = sharedRoot.resolve("results");
        bus.ensureDirectories(sharedRoot.resolve("input"), sharedRoot.resolve("processing"),
                sharedRoot.resolve("output"), resultsDir);
    }

    /**
     * Runs {@code transaction} synchronously through the configured {@link PipelineSequence}
     * and persists the terminal result. If a terminal result already exists for this
     * transaction id, returns it unchanged without re-running any agent (idempotency,
     * mirroring {@code Integrator.seedInput()}'s skip-and-log behavior).
     */
    public TransactionRecord submit(Transaction transaction) {
        Optional<TransactionRecord> existing = findById(transaction.transactionId());
        if (existing.isPresent()) {
            auditLogger.record(SOURCE_AGENT, transaction.transactionId(), "SKIPPED_DUPLICATE");
            return existing.get();
        }

        TransactionRecord result = executor.runToCompletion(TransactionRecord.received(transaction), sequence, agentsByName);
        bus.write(resultsDir, PipelineMessage.initial(SOURCE_AGENT, null, result));
        regenerateSummary();
        return result;
    }

    public Optional<TransactionRecord> findById(String transactionId) {
        return bus.find(resultsDir, transactionId).map(PipelineMessage::data);
    }

    public List<TransactionRecord> listAll() {
        return bus.listMessages(resultsDir).stream().map(PipelineMessage::data).toList();
    }

    /** Reads shared/results/pipeline-summary.json, if it exists yet. */
    public Optional<PipelineSummaryWriter.PipelineSummary> latestSummary() {
        Path summaryFile = resultsDir.resolve("pipeline-summary.json");
        if (!Files.exists(summaryFile)) {
            return Optional.empty();
        }
        try {
            return Optional.of(JsonMapper.instance().readValue(summaryFile.toFile(), PipelineSummaryWriter.PipelineSummary.class));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + summaryFile, e);
        }
    }

    private void regenerateSummary() {
        int total = bus.listMessages(resultsDir).size();
        summaryWriter.writeSummary(resultsDir, total, sequence.agentNames());
    }
}
