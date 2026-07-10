package com.homework6.pipeline.config;

import java.math.BigDecimal;

/**
 * Central home for pipeline thresholds. Per agents.md, no agent may hardcode a
 * fraud/compliance threshold inline — everything lives here.
 */
public final class PipelineConfig {

    private PipelineConfig() {
    }

    /** Country code treated as "domestic" for cross-border scoring. */
    public static final String HOME_COUNTRY = "US";

    /** Transactions outside this UTC hour window score as unusual timing. */
    public static final int BUSINESS_HOURS_START_UTC = 6;
    public static final int BUSINESS_HOURS_END_UTC = 22;

    /** Amount above which a transaction is considered high-value for fraud scoring. */
    public static final BigDecimal FRAUD_HIGH_VALUE_THRESHOLD = new BigDecimal("10000.00");

    /** Risk-score weights. High value alone meets the flag threshold by design (Mid-Level Objective #2). */
    public static final int HIGH_VALUE_WEIGHT = 70;
    public static final int UNUSUAL_TIMING_WEIGHT = 20;
    public static final int CROSS_BORDER_WEIGHT = 20;
    public static final int MAX_RISK_SCORE = 100;

    /** A transaction scoring at or above this is flagged for manual fraud review. */
    public static final int FRAUD_FLAG_THRESHOLD = 70;
}
