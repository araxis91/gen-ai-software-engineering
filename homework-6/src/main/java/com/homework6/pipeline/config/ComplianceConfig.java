package com.homework6.pipeline.config;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Compliance-specific rules: sanctions denylist and the cross-border wire hold threshold.
 */
public final class ComplianceConfig {

    private ComplianceConfig() {
    }

    /** Destination accounts blocked outright (sanctions/denylist simulation). */
    public static final Set<String> BLOCKED_ACCOUNTS = Set.of("ACC-9999");

    /** Cross-border wire transfers above this amount require manual compliance hold. */
    public static final BigDecimal WIRE_TRANSFER_HOLD_THRESHOLD = new BigDecimal("50000.00");

    public static final String WIRE_TRANSFER_TYPE = "wire_transfer";
}
