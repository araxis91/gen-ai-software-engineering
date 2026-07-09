package com.homework6.pipeline.cli;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidateTransactionsCliTest {

    @TempDir
    Path tempDir;

    private final PrintStream originalOut = System.out;
    private ByteArrayOutputStream captured;

    @BeforeEach
    void captureStdout() {
        captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured));
    }

    @AfterEach
    void restoreStdout() {
        System.setOut(originalOut);
    }

    @Test
    void run_mixedValidAndInvalidTransactions_reportsCountsAndReasonCodesWithoutTouchingSharedDirectories() throws IOException {
        Path file = tempDir.resolve("transactions.json");
        Files.writeString(file, """
                [
                  {
                    "transaction_id": "CTX001",
                    "timestamp": "2026-03-16T09:00:00Z",
                    "source_account": "ACC-1001",
                    "destination_account": "ACC-2001",
                    "amount": "1500.00",
                    "currency": "USD",
                    "transaction_type": "transfer",
                    "description": "valid",
                    "metadata": { "channel": "online", "country": "US" }
                  },
                  {
                    "transaction_id": "CTX002",
                    "timestamp": "2026-03-16T09:00:00Z",
                    "source_account": "ACC-1002",
                    "destination_account": "ACC-2002",
                    "amount": "-50.00",
                    "currency": "USD",
                    "transaction_type": "refund",
                    "description": "invalid",
                    "metadata": { "channel": "online", "country": "US" }
                  }
                ]
                """);

        new ValidateTransactionsCli().run(file);

        String output = captured.toString();
        assertTrue(output.contains("Total: 2   Valid: 1   Invalid: 1"));
        assertTrue(output.contains("CTX001"));
        assertTrue(output.contains("VALID"));
        assertTrue(output.contains("CTX002"));
        assertTrue(output.contains("NEGATIVE_AMOUNT"));
        assertTrue(Files.notExists(tempDir.resolve("shared")), "dry-run must never create a shared/ directory");
    }
}
