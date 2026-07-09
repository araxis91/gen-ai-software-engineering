package com.homework6.pipeline.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionStatusTest {

    @ParameterizedTest
    @EnumSource(value = TransactionStatus.class,
            names = {"REJECTED", "FLAGGED_FOR_REVIEW", "COMPLIANCE_HOLD", "SETTLED"})
    void isTerminal_terminalStatuses_returnsTrue(TransactionStatus status) {
        assertTrue(status.isTerminal());
    }

    @ParameterizedTest
    @EnumSource(value = TransactionStatus.class,
            names = {"RECEIVED", "VALIDATED", "FRAUD_CLEARED", "COMPLIANCE_CLEARED"})
    void isTerminal_nonTerminalStatuses_returnsFalse(TransactionStatus status) {
        assertFalse(status.isTerminal());
    }
}
