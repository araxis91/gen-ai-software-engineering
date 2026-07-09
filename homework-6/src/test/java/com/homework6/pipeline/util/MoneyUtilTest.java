package com.homework6.pipeline.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoneyUtilTest {

    @ParameterizedTest
    @ValueSource(strings = {"USD", "EUR", "GBP", "JPY"})
    void isValidCurrency_recognizedIso4217Codes_returnsTrue(String code) {
        assertTrue(MoneyUtil.isValidCurrency(code));
    }

    @Test
    void isValidCurrency_unknownCode_returnsFalse() {
        assertFalse(MoneyUtil.isValidCurrency("XYZ"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void isValidCurrency_nullOrBlank_returnsFalse(String code) {
        assertFalse(MoneyUtil.isValidCurrency(code));
    }

    @Test
    void isPositive_positiveAmount_returnsTrue() {
        assertTrue(MoneyUtil.isPositive(new BigDecimal("1500.00")));
    }

    @Test
    void isPositive_negativeAmount_returnsFalse() {
        assertFalse(MoneyUtil.isPositive(new BigDecimal("-100.00")));
    }

    @Test
    void isPositive_zeroAmount_returnsFalse() {
        assertFalse(MoneyUtil.isPositive(BigDecimal.ZERO));
    }

    @Test
    void isPositive_nullAmount_returnsFalse() {
        assertFalse(MoneyUtil.isPositive(null));
    }
}
