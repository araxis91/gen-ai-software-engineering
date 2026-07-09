package com.homework6.pipeline.exception;

/** Thrown when a transaction id has no terminal result yet in {@code shared/results/}. */
public class TransactionNotFoundException extends RuntimeException {

    private TransactionNotFoundException(String message) {
        super(message);
    }

    public static TransactionNotFoundException forTransactionId(String transactionId) {
        return new TransactionNotFoundException("No result found for transaction '" + transactionId + "'");
    }

    public static TransactionNotFoundException noSummaryAvailableYet() {
        return new TransactionNotFoundException(
                "No pipeline summary is available yet -- submit a transaction or run the pipeline first");
    }
}
