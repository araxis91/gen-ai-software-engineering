package com.homework6.pipeline.agent;

import com.homework6.pipeline.audit.AuditLogger;
import com.homework6.pipeline.model.PipelineMessage;
import com.homework6.pipeline.model.Transaction;
import com.homework6.pipeline.model.TransactionRecord;
import com.homework6.pipeline.util.MoneyUtil;

/**
 * Checks required fields, positive amount, and ISO 4217 currency before a transaction
 * is allowed to proceed to fraud detection. Each rule is an independently testable
 * private check, per agents.md testing conventions.
 */
public final class TransactionValidatorAgent implements PipelineAgent {

    public static final String NAME = "transaction_validator";
    private static final String NEXT_AGENT = "fraud_detector";

    private final AuditLogger auditLogger;

    public TransactionValidatorAgent(AuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public PipelineMessage process(PipelineMessage message) {
        TransactionRecord record = message.data();
        Transaction tx = record.transaction();

        String rejectionReasonCode = firstFailingRule(tx);
        if (rejectionReasonCode != null) {
            TransactionRecord rejected = record.withState(
                    record.state().rejected(rejectionReasonCode, describe(rejectionReasonCode)));
            auditLogger.recordWithAccounts(NAME, tx.transactionId(), "REJECTED:" + rejectionReasonCode,
                    tx.sourceAccount(), tx.destinationAccount());
            return message.terminal(NAME, rejected);
        }

        TransactionRecord validated = record.withState(record.state().validated());
        auditLogger.record(NAME, tx.transactionId(), "VALIDATED");
        return message.routedTo(NAME, NEXT_AGENT, validated);
    }

    private String firstFailingRule(Transaction tx) {
        if (!hasRequiredFields(tx)) {
            return "MISSING_REQUIRED_FIELD";
        }
        if (!hasPositiveAmount(tx)) {
            return "NEGATIVE_AMOUNT";
        }
        if (!hasValidCurrency(tx)) {
            return "INVALID_CURRENCY";
        }
        return null;
    }

    private boolean hasRequiredFields(Transaction tx) {
        return isNonBlank(tx.transactionId())
                && tx.timestamp() != null
                && isNonBlank(tx.sourceAccount())
                && isNonBlank(tx.destinationAccount())
                && tx.amount() != null
                && isNonBlank(tx.currency())
                && isNonBlank(tx.transactionType());
    }

    private boolean hasPositiveAmount(Transaction tx) {
        return MoneyUtil.isPositive(tx.amount());
    }

    private boolean hasValidCurrency(Transaction tx) {
        return MoneyUtil.isValidCurrency(tx.currency());
    }

    private boolean isNonBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String describe(String reasonCode) {
        return switch (reasonCode) {
            case "MISSING_REQUIRED_FIELD" -> "One or more required transaction fields is missing or blank";
            case "NEGATIVE_AMOUNT" -> "Transaction amount must be a positive value";
            case "INVALID_CURRENCY" -> "Currency code is not a recognized ISO 4217 code";
            default -> "Transaction failed validation";
        };
    }
}
