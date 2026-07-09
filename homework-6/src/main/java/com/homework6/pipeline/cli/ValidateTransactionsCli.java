package com.homework6.pipeline.cli;

import com.homework6.pipeline.agent.TransactionValidatorAgent;
import com.homework6.pipeline.audit.AuditLogger;
import com.homework6.pipeline.messaging.JsonMapper;
import com.homework6.pipeline.model.PipelineMessage;
import com.homework6.pipeline.model.Transaction;
import com.homework6.pipeline.model.TransactionRecord;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Dry-run entry point for {@code /validate-transactions}: runs only the
 * TransactionValidatorAgent, entirely in memory, with no shared/ directory I/O.
 * Equivalent to `python agents/transaction_validator.py --dry-run` in TASKS.md.
 */
public final class ValidateTransactionsCli {

    private final TransactionValidatorAgent validator = new TransactionValidatorAgent(new AuditLogger());

    public static void main(String[] args) {
        Path file = Path.of(args.length > 0 ? args[0] : "sample-transactions.json");
        new ValidateTransactionsCli().run(file);
    }

    public void run(Path sampleTransactionsFile) {
        List<Transaction> transactions = loadTransactions(sampleTransactionsFile);
        List<PipelineMessage> results = new ArrayList<>();

        for (Transaction tx : transactions) {
            PipelineMessage initial = PipelineMessage.initial("validate-transactions-cli", TransactionValidatorAgent.NAME,
                    TransactionRecord.received(tx));
            results.add(validator.process(initial));
        }

        printReport(transactions.size(), results);
    }

    private List<Transaction> loadTransactions(Path file) {
        try {
            Transaction[] array = JsonMapper.instance().readValue(file.toFile(), Transaction[].class);
            return List.of(array);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load " + file, e);
        }
    }

    private void printReport(int total, List<PipelineMessage> results) {
        long validCount = results.stream()
                .filter(m -> m.data().state().status().name().equals("VALIDATED"))
                .count();
        long invalidCount = total - validCount;

        System.out.println();
        System.out.println("Dry-run validation report");
        System.out.println("==========================");
        System.out.printf("Total: %d   Valid: %d   Invalid: %d%n%n", total, validCount, invalidCount);

        System.out.printf("%-10s %-10s %-28s %s%n", "TXN_ID", "RESULT", "REASON_CODE", "REASON");
        System.out.println("-".repeat(90));
        for (PipelineMessage message : results) {
            var record = message.data();
            String result = record.state().status().name().equals("VALIDATED") ? "VALID" : "INVALID";
            String reasonCode = record.state().reasonCode() == null ? "-" : record.state().reasonCode();
            String reason = record.state().reason() == null ? "-" : record.state().reason();
            System.out.printf("%-10s %-10s %-28s %s%n", record.transactionId(), result, reasonCode, reason);
        }
        System.out.println();
    }
}
