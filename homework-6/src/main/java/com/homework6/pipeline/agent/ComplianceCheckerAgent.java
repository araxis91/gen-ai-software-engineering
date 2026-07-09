package com.homework6.pipeline.agent;

import com.homework6.pipeline.audit.AuditLogger;
import com.homework6.pipeline.config.ComplianceConfig;
import com.homework6.pipeline.model.Transaction;
import com.homework6.pipeline.model.TransactionRecord;
import com.homework6.pipeline.util.PiiMaskingUtil;

import java.util.Objects;

/**
 * Rejects transactions to denylisted destination accounts outright, and puts large
 * cross-border wire transfers on a manual compliance hold rather than rejecting them.
 * Denylist and threshold live in {@link ComplianceConfig}. Does not decide what runs
 * next — see {@code com.homework6.pipeline.PipelineSequence}.
 */
public final class ComplianceCheckerAgent implements PipelineAgent {

    public static final String NAME = "compliance_checker";

    private final AuditLogger auditLogger;

    public ComplianceCheckerAgent(AuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public TransactionRecord process(TransactionRecord record) {
        Transaction tx = record.transaction();

        if (isBlockedDestination(tx)) {
            String reason = "Destination account " + PiiMaskingUtil.mask(tx.destinationAccount())
                    + " is on the compliance denylist";
            auditLogger.recordWithAccounts(NAME, tx.transactionId(), "REJECTED:BLOCKED_DESTINATION_ACCOUNT",
                    tx.sourceAccount(), tx.destinationAccount());
            return record.withState(record.state().rejected("BLOCKED_DESTINATION_ACCOUNT", reason));
        }

        if (requiresManualHold(tx)) {
            auditLogger.record(NAME, tx.transactionId(), "COMPLIANCE_HOLD:LARGE_CROSS_BORDER_WIRE");
            return record.withState(record.state().complianceHold(
                    "LARGE_CROSS_BORDER_WIRE",
                    "Cross-border wire transfer above the compliance hold threshold requires manual review"));
        }

        auditLogger.record(NAME, tx.transactionId(), "COMPLIANCE_CLEARED");
        return record.withState(record.state().complianceCleared());
    }

    boolean isBlockedDestination(Transaction tx) {
        return ComplianceConfig.BLOCKED_ACCOUNTS.contains(tx.destinationAccount());
    }

    boolean requiresManualHold(Transaction tx) {
        boolean isWireTransfer = Objects.equals(tx.transactionType(), ComplianceConfig.WIRE_TRANSFER_TYPE);
        boolean isCrossBorder = tx.country() != null
                && !Objects.equals(tx.country(), com.homework6.pipeline.config.PipelineConfig.HOME_COUNTRY);
        boolean isOverThreshold = tx.amount() != null
                && tx.amount().compareTo(ComplianceConfig.WIRE_TRANSFER_HOLD_THRESHOLD) > 0;
        return isWireTransfer && isCrossBorder && isOverThreshold;
    }
}
