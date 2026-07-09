package com.homework6.pipeline.agent;

import com.homework6.pipeline.audit.AuditLogger;
import com.homework6.pipeline.config.PipelineConfig;
import com.homework6.pipeline.model.Transaction;
import com.homework6.pipeline.model.TransactionRecord;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Scores a validated transaction 0-100 across three weighted factors (high value,
 * unusual timing, cross-border) and flags it for manual review at or above the
 * configured threshold. Weights/thresholds live in {@link PipelineConfig}. Does not
 * decide what runs next — see {@code com.homework6.pipeline.PipelineSequence}.
 */
public final class FraudDetectorAgent implements PipelineAgent {

    public static final String NAME = "fraud_detector";

    private final AuditLogger auditLogger;

    public FraudDetectorAgent(AuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public TransactionRecord process(TransactionRecord record) {
        Transaction tx = record.transaction();

        List<String> factors = new ArrayList<>();
        int score = 0;

        if (isHighValue(tx)) {
            score += PipelineConfig.HIGH_VALUE_WEIGHT;
            factors.add("high_value");
        }
        if (isUnusualTiming(tx)) {
            score += PipelineConfig.UNUSUAL_TIMING_WEIGHT;
            factors.add("unusual_timing");
        }
        if (isCrossBorder(tx)) {
            score += PipelineConfig.CROSS_BORDER_WEIGHT;
            factors.add("cross_border");
        }
        score = Math.min(score, PipelineConfig.MAX_RISK_SCORE);

        if (score >= PipelineConfig.FRAUD_FLAG_THRESHOLD) {
            auditLogger.record(NAME, tx.transactionId(), "FLAGGED_FOR_REVIEW:score=" + score);
            return record.withState(record.state().flaggedForReview(score, factors));
        }

        auditLogger.record(NAME, tx.transactionId(), "FRAUD_CLEARED:score=" + score);
        return record.withState(record.state().fraudCleared(score, factors));
    }

    boolean isHighValue(Transaction tx) {
        return tx.amount() != null && tx.amount().compareTo(PipelineConfig.FRAUD_HIGH_VALUE_THRESHOLD) > 0;
    }

    boolean isUnusualTiming(Transaction tx) {
        OffsetDateTime utc = tx.timestamp().withOffsetSameInstant(java.time.ZoneOffset.UTC);
        int hour = utc.getHour();
        return hour < PipelineConfig.BUSINESS_HOURS_START_UTC || hour >= PipelineConfig.BUSINESS_HOURS_END_UTC;
    }

    boolean isCrossBorder(Transaction tx) {
        String country = tx.country();
        return country != null && !Objects.equals(country, PipelineConfig.HOME_COUNTRY);
    }
}
