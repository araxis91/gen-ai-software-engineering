package com.homework6.pipeline.util;

import java.math.BigDecimal;
import java.util.Currency;

/**
 * Monetary helpers. BigDecimal only — never double/float (agents.md, Hard Rule: Money).
 */
public final class MoneyUtil {

    private MoneyUtil() {
    }

    /**
     * @return true if {@code currencyCode} is a valid ISO 4217 code recognized by the JVM.
     */
    public static boolean isValidCurrency(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            return false;
        }
        try {
            Currency.getInstance(currencyCode);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static boolean isPositive(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }
}
