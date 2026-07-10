package com.homework6.pipeline.model;

/**
 * The full payload agents pass to each other: the immutable original {@link Transaction}
 * plus the current {@link ProcessingState}.
 */
public record TransactionRecord(Transaction transaction, ProcessingState state) {

    public static TransactionRecord received(Transaction transaction) {
        return new TransactionRecord(transaction, ProcessingState.received());
    }

    public TransactionRecord withState(ProcessingState newState) {
        return new TransactionRecord(transaction, newState);
    }

    public String transactionId() {
        return transaction.transactionId();
    }
}
