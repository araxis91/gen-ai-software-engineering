package com.homework6.pipeline;

import com.homework6.pipeline.messaging.FileMessageBus;
import com.homework6.pipeline.model.PipelineMessage;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Regenerates {@code shared/results/pipeline-summary.json} from whatever is currently
 * in {@code shared/results/}. Shared by the CLI ({@link Integrator}, after a batch run)
 * and the REST API ({@code PipelineExecutionService}, after each synchronous
 * submission) so both entry points keep the summary file consistent with the actual
 * contents of {@code shared/results/}, not just with their own view of a single run.
 */
public final class PipelineSummaryWriter {

    private final FileMessageBus bus;

    public PipelineSummaryWriter(FileMessageBus bus) {
        this.bus = bus;
    }

    public Map<String, Long> writeSummary(Path resultsDir, int totalTransactions, List<String> agentSequence) {
        List<PipelineMessage> outcomes = bus.listMessages(resultsDir);

        Map<String, Long> countsByStatus = new LinkedHashMap<>();
        for (PipelineMessage outcome : outcomes) {
            String status = outcome.data().state().status().name();
            countsByStatus.merge(status, 1L, Long::sum);
        }

        PipelineSummary summary = new PipelineSummary(
                OffsetDateTime.now(ZoneOffset.UTC),
                totalTransactions,
                outcomes.size(),
                agentSequence,
                countsByStatus,
                outcomes.stream().map(this::toOutcomeSummary).toList());

        bus.writeJson(resultsDir.resolve("pipeline-summary.json"), summary);
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

    public record PipelineSummary(
            OffsetDateTime generatedAt,
            int totalTransactions,
            int resultsWritten,
            List<String> agentSequence,
            Map<String, Long> countsByStatus,
            List<TransactionOutcomeSummary> outcomes) {
    }

    public record TransactionOutcomeSummary(String transactionId, String status, String reasonCode, Integer riskScore) {
    }
}
