package com.homework6.pipeline.messaging;

import com.homework6.pipeline.model.PipelineMessage;
import com.homework6.pipeline.model.Transaction;
import com.homework6.pipeline.model.TransactionRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileMessageBusTest {

    @TempDir
    Path tempDir;

    private final FileMessageBus bus = new FileMessageBus();

    private PipelineMessage message(String transactionId, String targetAgent) {
        Transaction transaction = new Transaction(
                transactionId, OffsetDateTime.parse("2026-03-16T09:00:00Z"), "ACC-1001", "ACC-2001",
                new BigDecimal("1500.00"), "USD", "transfer", "test", Map.of("channel", "online", "country", "US"));
        return PipelineMessage.initial("integrator", targetAgent, TransactionRecord.received(transaction));
    }

    @Test
    void ensureDirectories_createsMissingDirectories() {
        Path dir = tempDir.resolve("nested/dir");
        bus.ensureDirectories(dir);
        assertTrue(Files.isDirectory(dir));
    }

    @Test
    void write_thenFind_roundTripsMessage() {
        PipelineMessage original = message("TXN001", "fraud_detector");
        bus.write(tempDir, original);

        Optional<PipelineMessage> found = bus.find(tempDir, "TXN001");

        assertTrue(found.isPresent());
        assertEquals("TXN001", found.get().data().transactionId());
        assertEquals("fraud_detector", found.get().targetAgent());
    }

    @Test
    void write_leavesNoTmpFileBehind() throws Exception {
        bus.write(tempDir, message("TXN001", "fraud_detector"));

        try (var stream = Files.list(tempDir)) {
            assertTrue(stream.noneMatch(p -> p.toString().endsWith(".tmp")));
        }
    }

    @Test
    void find_missingTransaction_returnsEmpty() {
        assertTrue(bus.find(tempDir, "TXN-MISSING").isEmpty());
    }

    @Test
    void exists_afterWrite_returnsTrue() {
        bus.write(tempDir, message("TXN001", "fraud_detector"));
        assertTrue(bus.exists(tempDir, "TXN001"));
    }

    @Test
    void exists_beforeWrite_returnsFalse() {
        assertFalse(bus.exists(tempDir, "TXN001"));
    }

    @Test
    void listMessages_skipsPipelineSummaryFile() throws Exception {
        bus.write(tempDir, message("TXN001", "fraud_detector"));
        bus.writeJson(tempDir.resolve("pipeline-summary.json"), Map.of("total", 1));

        List<PipelineMessage> messages = bus.listMessages(tempDir);

        assertEquals(1, messages.size());
        assertEquals("TXN001", messages.get(0).data().transactionId());
    }

    @Test
    void listByTargetAgent_filtersOnTargetAgent() {
        bus.write(tempDir, message("TXN001", "fraud_detector"));
        bus.write(tempDir, message("TXN002", "compliance_checker"));

        List<PipelineMessage> fraudTargeted = bus.listByTargetAgent(tempDir, "fraud_detector");

        assertEquals(1, fraudTargeted.size());
        assertEquals("TXN001", fraudTargeted.get(0).data().transactionId());
    }

    @Test
    void moveRaw_relocatesFileBetweenDirectories() throws Exception {
        Path from = Files.createDirectory(tempDir.resolve("from"));
        Path to = Files.createDirectory(tempDir.resolve("to"));
        bus.write(from, message("TXN001", "fraud_detector"));

        bus.moveRaw(from, to, "TXN001");

        assertFalse(Files.exists(from.resolve("TXN001.json")));
        assertTrue(Files.exists(to.resolve("TXN001.json")));
    }

    @Test
    void delete_removesExistingFile_andIsNoOpWhenAlreadyAbsent() {
        bus.write(tempDir, message("TXN001", "fraud_detector"));

        bus.delete(tempDir, "TXN001");
        assertFalse(bus.exists(tempDir, "TXN001"));

        bus.delete(tempDir, "TXN001");
        assertFalse(bus.exists(tempDir, "TXN001"));
    }
}
