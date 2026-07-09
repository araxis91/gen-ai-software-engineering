package com.homework6.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.homework6.pipeline.messaging.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Full end-to-end run of the pipeline: validator -> fraud detector -> compliance
 * checker -> settlement processor, driven entirely against a @TempDir shared/ tree
 * and a temp transactions file -- never touches the real shared/ directories.
 */
class IntegratorTest {

    @TempDir
    Path tempDir;

    private Path sharedRoot;
    private Path sampleTransactionsFile;

    @BeforeEach
    void setUp() throws IOException {
        sharedRoot = tempDir.resolve("shared");
        sampleTransactionsFile = tempDir.resolve("sample-transactions.json");
        Files.writeString(sampleTransactionsFile, """
                [
                  {
                    "transaction_id": "ITX001",
                    "timestamp": "2026-03-16T09:00:00Z",
                    "source_account": "ACC-1001",
                    "destination_account": "ACC-2001",
                    "amount": "1500.00",
                    "currency": "USD",
                    "transaction_type": "transfer",
                    "description": "settles cleanly",
                    "metadata": { "channel": "online", "country": "US" }
                  },
                  {
                    "transaction_id": "ITX002",
                    "timestamp": "2026-03-16T09:00:00Z",
                    "source_account": "ACC-1002",
                    "destination_account": "ACC-2002",
                    "amount": "200.00",
                    "currency": "ZZZ",
                    "transaction_type": "transfer",
                    "description": "invalid currency",
                    "metadata": { "channel": "online", "country": "US" }
                  },
                  {
                    "transaction_id": "ITX003",
                    "timestamp": "2026-03-16T09:00:00Z",
                    "source_account": "ACC-1003",
                    "destination_account": "ACC-9999",
                    "amount": "500.00",
                    "currency": "USD",
                    "transaction_type": "transfer",
                    "description": "blocked destination account",
                    "metadata": { "channel": "online", "country": "US" }
                  },
                  {
                    "transaction_id": "ITX004",
                    "timestamp": "2026-03-16T09:00:00Z",
                    "source_account": "ACC-1004",
                    "destination_account": "ACC-2004",
                    "amount": "25000.00",
                    "currency": "USD",
                    "transaction_type": "wire_transfer",
                    "description": "high value flagged for review",
                    "metadata": { "channel": "branch", "country": "US" }
                  }
                ]
                """);
    }

    private JsonNode readResult(String transactionId) throws IOException {
        Path path = sharedRoot.resolve("results").resolve(transactionId + ".json");
        return JsonMapper.instance().readTree(path.toFile());
    }

    @Test
    void run_processesEveryTransactionToATerminalResult() throws IOException {
        new Integrator(sharedRoot, sampleTransactionsFile).run();

        assertEquals("SETTLED", readResult("ITX001").at("/data/state/status").asText());
        assertEquals("REJECTED", readResult("ITX002").at("/data/state/status").asText());
        assertEquals("INVALID_CURRENCY", readResult("ITX002").at("/data/state/reason_code").asText());
        assertEquals("REJECTED", readResult("ITX003").at("/data/state/status").asText());
        assertEquals("BLOCKED_DESTINATION_ACCOUNT", readResult("ITX003").at("/data/state/reason_code").asText());
        assertEquals("FLAGGED_FOR_REVIEW", readResult("ITX004").at("/data/state/status").asText());
    }

    @Test
    void run_leavesInputProcessingAndOutputDirectoriesEmpty() throws IOException {
        new Integrator(sharedRoot, sampleTransactionsFile).run();

        assertTrue(isEmpty(sharedRoot.resolve("input")));
        assertTrue(isEmpty(sharedRoot.resolve("processing")));
        assertTrue(isEmpty(sharedRoot.resolve("output")));
    }

    @Test
    void run_writesPipelineSummaryWithCorrectCounts() throws IOException {
        new Integrator(sharedRoot, sampleTransactionsFile).run();

        JsonNode summary = JsonMapper.instance().readTree(
                sharedRoot.resolve("results").resolve("pipeline-summary.json").toFile());

        assertEquals(4, summary.at("/total_transactions").asInt());
        assertEquals(1, summary.at("/counts_by_status/SETTLED").asInt());
        assertEquals(2, summary.at("/counts_by_status/REJECTED").asInt());
        assertEquals(1, summary.at("/counts_by_status/FLAGGED_FOR_REVIEW").asInt());
    }

    @Test
    void run_calledTwice_isIdempotentAndSkipsAlreadyTerminalTransactions() throws IOException {
        Integrator integrator = new Integrator(sharedRoot, sampleTransactionsFile);
        integrator.run();
        String settledAtFirstRun = readResult("ITX001").at("/data/state/settled_at").asText();

        integrator.run();
        String settledAtSecondRun = readResult("ITX001").at("/data/state/settled_at").asText();

        assertEquals(settledAtFirstRun, settledAtSecondRun, "re-running must not re-settle an already-terminal transaction");
    }

    @Test
    void run_withSubsetSequence_skipsUnconfiguredStages() throws IOException {
        // Skips fraud detection and compliance entirely -- ITX004 (normally flagged for
        // high value) settles instead, proving the sequence, not the agent, decides routing.
        PipelineSequence subset = PipelineSequence.of("transaction_validator", "settlement_processor");

        new Integrator(sharedRoot, sampleTransactionsFile, subset).run();

        assertEquals("SETTLED", readResult("ITX001").at("/data/state/status").asText());
        assertEquals("REJECTED", readResult("ITX002").at("/data/state/status").asText(), "validator still runs first");
        assertEquals("SETTLED", readResult("ITX003").at("/data/state/status").asText(),
                "compliance checker not in this sequence, so the blocked account is never caught");
        assertEquals("SETTLED", readResult("ITX004").at("/data/state/status").asText(),
                "fraud detector not in this sequence, so the high-value flag never triggers");
    }

    @Test
    void run_withReversedSequence_firstConfiguredAgentReceivesTheSeededMessages() throws IOException {
        // compliance_checker runs before fraud/validation -- still catches the blocked account.
        PipelineSequence reversed = PipelineSequence.of("compliance_checker", "settlement_processor");

        new Integrator(sharedRoot, sampleTransactionsFile, reversed).run();

        assertEquals("REJECTED", readResult("ITX003").at("/data/state/status").asText());
        assertEquals("BLOCKED_DESTINATION_ACCOUNT", readResult("ITX003").at("/data/state/reason_code").asText());
        assertEquals("SETTLED", readResult("ITX001").at("/data/state/status").asText());
        assertEquals("SETTLED", readResult("ITX002").at("/data/state/status").asText(),
                "validator not in this sequence, so the invalid currency is never caught");
    }

    @Test
    void constructor_sequenceWithUnknownAgentName_throws() {
        PipelineSequence bogus = PipelineSequence.of("not_a_real_agent");
        assertThrows(IllegalArgumentException.class, () -> new Integrator(sharedRoot, sampleTransactionsFile, bogus));
    }

    @Test
    void seedInputThenRunStage_calledIndividuallyOneAtATime_reachesSameResultAsRun() throws IOException {
        Integrator integrator = new Integrator(sharedRoot, sampleTransactionsFile);

        integrator.seedInput();
        integrator.runStage("transaction_validator");
        integrator.runStage("fraud_detector");
        integrator.runStage("compliance_checker");
        integrator.runStage("settlement_processor");

        assertEquals("SETTLED", readResult("ITX001").at("/data/state/status").asText());
        assertEquals("REJECTED", readResult("ITX003").at("/data/state/status").asText());
    }

    @Test
    void runStage_calledBeforeAnythingIsQueuedForIt_processesZeroMessagesWithoutError() {
        Integrator integrator = new Integrator(sharedRoot, sampleTransactionsFile);

        assertDoesNotThrow(() -> integrator.runStage("settlement_processor"));
    }

    @Test
    void runStage_unknownAgentName_throws() {
        Integrator integrator = new Integrator(sharedRoot, sampleTransactionsFile);
        assertThrows(IllegalArgumentException.class, () -> integrator.runStage("not_a_real_agent"));
    }

    private boolean isEmpty(Path dir) throws IOException {
        try (var stream = Files.list(dir)) {
            return stream.findAny().isEmpty();
        }
    }
}
