package com.homework6.pipeline.agent;

import com.homework6.pipeline.audit.AuditLogger;
import com.homework6.pipeline.model.Transaction;
import com.homework6.pipeline.model.TransactionRecord;
import com.homework6.pipeline.model.TransactionStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransactionValidatorAgentTest {

    private final TransactionValidatorAgent agent = new TransactionValidatorAgent(new AuditLogger());

    private Transaction validTransaction() {
        return new Transaction("TXN001", OffsetDateTime.parse("2026-03-16T09:00:00Z"), "ACC-1001", "ACC-2001",
                new BigDecimal("1500.00"), "USD", "transfer", "Monthly rent payment",
                Map.of("channel", "online", "country", "US"));
    }

    private TransactionRecord process(Transaction transaction) {
        return agent.process(TransactionRecord.received(transaction));
    }

    @Test
    void process_validTransaction_marksValidated() {
        TransactionRecord result = process(validTransaction());

        assertEquals(TransactionStatus.VALIDATED, result.state().status());
    }

    @Test
    void process_negativeAmount_rejectsWithNegativeAmountReasonCode() {
        Transaction transaction = new Transaction("TXN007", OffsetDateTime.parse("2026-03-16T10:10:00Z"),
                "ACC-1007", "ACC-8800", new BigDecimal("-100.00"), "GBP", "refund", "Refund for order #8821",
                Map.of("channel", "online", "country", "GB"));

        TransactionRecord result = process(transaction);

        assertEquals(TransactionStatus.REJECTED, result.state().status());
        assertEquals("NEGATIVE_AMOUNT", result.state().reasonCode());
    }

    @Test
    void process_invalidCurrency_rejectsWithInvalidCurrencyReasonCode() {
        Transaction transaction = new Transaction("TXN006", OffsetDateTime.parse("2026-03-16T10:05:00Z"),
                "ACC-1006", "ACC-7700", new BigDecimal("200.00"), "XYZ", "transfer", "Test payment",
                Map.of("channel", "online", "country", "US"));

        TransactionRecord result = process(transaction);

        assertEquals(TransactionStatus.REJECTED, result.state().status());
        assertEquals("INVALID_CURRENCY", result.state().reasonCode());
    }

    @Test
    void process_missingSourceAccount_rejectsWithMissingRequiredFieldReasonCode() {
        Transaction transaction = new Transaction("TXN009", OffsetDateTime.parse("2026-03-16T10:05:00Z"),
                "", "ACC-7700", new BigDecimal("200.00"), "USD", "transfer", "Test payment",
                Map.of("channel", "online", "country", "US"));

        TransactionRecord result = process(transaction);

        assertEquals(TransactionStatus.REJECTED, result.state().status());
        assertEquals("MISSING_REQUIRED_FIELD", result.state().reasonCode());
    }

    @Test
    void process_zeroAmount_rejectsWithNegativeAmountReasonCode() {
        Transaction transaction = new Transaction("TXN010", OffsetDateTime.parse("2026-03-16T10:05:00Z"),
                "ACC-1001", "ACC-2001", BigDecimal.ZERO, "USD", "transfer", "Zero amount",
                Map.of("channel", "online", "country", "US"));

        TransactionRecord result = process(transaction);

        assertEquals(TransactionStatus.REJECTED, result.state().status());
        assertEquals("NEGATIVE_AMOUNT", result.state().reasonCode());
    }

    @Test
    void name_returnsTransactionValidatorConstant() {
        assertEquals("transaction_validator", agent.name());
    }
}
