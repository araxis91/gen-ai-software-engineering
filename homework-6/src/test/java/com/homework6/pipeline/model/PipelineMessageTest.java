package com.homework6.pipeline.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PipelineMessageTest {

    private final Transaction transaction = new Transaction(
            "TXN001", OffsetDateTime.parse("2026-03-16T09:00:00Z"), "ACC-1001", "ACC-2001",
            new BigDecimal("1500.00"), "USD", "transfer", "Monthly rent payment",
            Map.of("channel", "online", "country", "US"));
    private final TransactionRecord record = TransactionRecord.received(transaction);

    @Test
    void initial_setsSourceAndTargetAgentAndTransactionMessageType() {
        PipelineMessage message = PipelineMessage.initial("integrator", "transaction_validator", record);

        assertEquals("integrator", message.sourceAgent());
        assertEquals("transaction_validator", message.targetAgent());
        assertEquals("transaction", message.messageType());
        assertEquals(record, message.data());
    }

    @Test
    void routedTo_generatesNewMessageIdAndUpdatesRoutingAndData() {
        PipelineMessage initial = PipelineMessage.initial("integrator", "transaction_validator", record);
        TransactionRecord validated = record.withState(record.state().validated());

        PipelineMessage routed = initial.routedTo("transaction_validator", "fraud_detector", validated);

        assertEquals("transaction_validator", routed.sourceAgent());
        assertEquals("fraud_detector", routed.targetAgent());
        assertEquals(validated, routed.data());
        assertNotEquals(initial.messageId(), routed.messageId());
    }

    @Test
    void terminal_clearsTargetAgent() {
        PipelineMessage initial = PipelineMessage.initial("integrator", "transaction_validator", record);
        TransactionRecord rejected = record.withState(record.state().rejected("NEGATIVE_AMOUNT", "bad amount"));

        PipelineMessage terminalMessage = initial.terminal("transaction_validator", rejected);

        assertEquals("transaction_validator", terminalMessage.sourceAgent());
        assertNull(terminalMessage.targetAgent());
        assertEquals(rejected, terminalMessage.data());
    }
}
