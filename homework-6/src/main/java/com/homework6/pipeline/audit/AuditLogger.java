package com.homework6.pipeline.audit;

import com.homework6.pipeline.util.PiiMaskingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * The single component through which every state-transition audit entry is recorded
 * (agents.md: Audit Trail rule — no ad-hoc logging of transitions elsewhere).
 * Never logs a raw account reference; always routes through {@link PiiMaskingUtil}.
 */
public final class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger("AUDIT");

    public void record(String agentName, String transactionId, String outcome) {
        log.info("timestamp={} agent={} transaction_id={} outcome={}",
                OffsetDateTime.now(ZoneOffset.UTC), agentName, transactionId, outcome);
    }

    public void recordWithAccounts(String agentName, String transactionId, String outcome,
                                    String sourceAccount, String destinationAccount) {
        log.info("timestamp={} agent={} transaction_id={} outcome={} source_account={} destination_account={}",
                OffsetDateTime.now(ZoneOffset.UTC), agentName, transactionId, outcome,
                PiiMaskingUtil.mask(sourceAccount), PiiMaskingUtil.mask(destinationAccount));
    }
}
