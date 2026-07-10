package com.homework6.pipeline.agent;

import com.homework6.pipeline.audit.AuditLogger;
import com.homework6.pipeline.model.PipelineMessage;
import com.homework6.pipeline.model.Transaction;
import com.homework6.pipeline.model.TransactionRecord;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Final hop in the chain: produces the SETTLED terminal record. Never routes onward.
 * Duplicate-settlement idempotency is enforced by the Integrator (which checks
 * {@code shared/results/} before invoking any agent for a given transaction) rather
 * than here, since agents are pure transforms with no file-system access
 * (see PipelineAgent.process javadoc).
 */
public final class SettlementProcessorAgent implements PipelineAgent {

    public static final String NAME = "settlement_processor";

    private final AuditLogger auditLogger;

    public SettlementProcessorAgent(AuditLogger auditLogger) {
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

        String settlementId = UUID.randomUUID().toString();
        OffsetDateTime settledAt = OffsetDateTime.now(ZoneOffset.UTC);

        TransactionRecord settled = record.withState(record.state().settled(settlementId, settledAt));
        auditLogger.record(NAME, tx.transactionId(), "SETTLED:" + settlementId);
        return message.terminal(NAME, settled);
    }
}
