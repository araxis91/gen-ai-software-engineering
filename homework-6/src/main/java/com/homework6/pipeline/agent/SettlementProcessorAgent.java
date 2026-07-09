package com.homework6.pipeline.agent;

import com.homework6.pipeline.audit.AuditLogger;
import com.homework6.pipeline.model.Transaction;
import com.homework6.pipeline.model.TransactionRecord;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Produces the SETTLED terminal record. Even when configured to run before other
 * stages (see {@code com.homework6.pipeline.PipelineSequence}), a SETTLED status is
 * always terminal — {@link com.homework6.pipeline.model.TransactionStatus#isTerminal()}
 * stops the orchestrator from routing it any further. Duplicate-settlement idempotency
 * is enforced by the Integrator (which checks {@code shared/results/} before invoking
 * any agent for a given transaction) rather than here, since agents are pure transforms
 * with no file-system access (see PipelineAgent.process javadoc).
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
    public TransactionRecord process(TransactionRecord record) {
        Transaction tx = record.transaction();

        String settlementId = UUID.randomUUID().toString();
        OffsetDateTime settledAt = OffsetDateTime.now(ZoneOffset.UTC);

        auditLogger.record(NAME, tx.transactionId(), "SETTLED:" + settlementId);
        return record.withState(record.state().settled(settlementId, settledAt));
    }
}
