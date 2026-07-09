package com.homework6.pipeline;

import com.homework6.pipeline.messaging.FileMessageBus;
import com.homework6.pipeline.messaging.JsonMapper;
import com.homework6.pipeline.model.PipelineMessage;
import com.homework6.pipeline.model.Transaction;
import com.homework6.pipeline.model.TransactionRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineSummaryWriterTest {

    @TempDir
    Path resultsDir;

    private final FileMessageBus bus = new FileMessageBus();
    private final PipelineSummaryWriter writer = new PipelineSummaryWriter(bus);

    private void writeResult(String transactionId, String status) {
        Transaction tx = new Transaction(transactionId, OffsetDateTime.parse("2026-03-16T09:00:00Z"),
                "ACC-1001", "ACC-2001", new BigDecimal("100.00"), "USD", "transfer", "test",
                Map.of("channel", "online", "country", "US"));
        var record = TransactionRecord.received(tx)
                .withState(TransactionRecord.received(tx).state().validated()
                        .fraudCleared(0, List.of()).complianceCleared()
                        .settled("settlement-" + transactionId, OffsetDateTime.now()));
        PipelineMessage message = PipelineMessage.initial("test", null, record);
        bus.write(resultsDir, message);
    }

    @Test
    void writeSummary_countsOnlyActualResultFiles_excludingTheSummaryFileItself() {
        writeResult("TXN001", "SETTLED");
        writeResult("TXN002", "SETTLED");

        Map<String, Long> counts = writer.writeSummary(resultsDir, 2, List.of("transaction_validator"));

        assertEquals(2L, counts.get("SETTLED"));
        // Re-running must not count pipeline-summary.json itself as an outcome.
        Map<String, Long> countsAfterRerun = writer.writeSummary(resultsDir, 2, List.of("transaction_validator"));
        assertEquals(2L, countsAfterRerun.get("SETTLED"));
    }

    @Test
    void writeSummary_writesReadableJsonFileWithExpectedFields() throws IOException {
        writeResult("TXN001", "SETTLED");

        writer.writeSummary(resultsDir, 1, List.of("transaction_validator", "settlement_processor"));

        var summary = JsonMapper.instance()
                .readValue(resultsDir.resolve("pipeline-summary.json").toFile(), PipelineSummaryWriter.PipelineSummary.class);

        assertEquals(1, summary.totalTransactions());
        assertEquals(1, summary.resultsWritten());
        assertEquals(List.of("transaction_validator", "settlement_processor"), summary.agentSequence());
        assertEquals(1, summary.outcomes().size());
        assertEquals("TXN001", summary.outcomes().get(0).transactionId());
    }

    @Test
    void writeSummary_emptyResultsDir_producesZeroCounts() {
        Map<String, Long> counts = writer.writeSummary(resultsDir, 0, List.of("transaction_validator"));
        assertTrue(counts.isEmpty());
    }
}
