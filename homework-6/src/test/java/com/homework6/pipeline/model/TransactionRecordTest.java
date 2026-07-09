package com.homework6.pipeline.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class TransactionRecordTest {

    private final Transaction transaction = new Transaction(
            "TXN001", OffsetDateTime.parse("2026-03-16T09:00:00Z"), "ACC-1001", "ACC-2001",
            new BigDecimal("1500.00"), "USD", "transfer", "Monthly rent payment",
            Map.of("channel", "online", "country", "US"));

    @Test
    void received_wrapsTransactionWithReceivedState() {
        TransactionRecord record = TransactionRecord.received(transaction);

        assertEquals(transaction, record.transaction());
        assertEquals(TransactionStatus.RECEIVED, record.state().status());
    }

    @Test
    void transactionId_delegatesToTransaction() {
        TransactionRecord record = TransactionRecord.received(transaction);
        assertEquals("TXN001", record.transactionId());
    }

    @Test
    void withState_replacesStateButKeepsSameTransactionInstance() {
        TransactionRecord original = TransactionRecord.received(transaction);
        ProcessingState newState = original.state().validated();

        TransactionRecord updated = original.withState(newState);

        assertSame(transaction, updated.transaction());
        assertEquals(TransactionStatus.VALIDATED, updated.state().status());
        assertEquals(TransactionStatus.RECEIVED, original.state().status(), "original record must stay unchanged");
    }
}
