package com.homework6.pipeline.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PiiMaskingUtilTest {

    @Test
    void mask_accountRefWithPrefixAndFourPlusDigits_masksAllButLastFour() {
        assertEquals("ACC-****1001", PiiMaskingUtil.mask("ACC-1001"));
    }

    @Test
    void mask_nullInput_returnsNull() {
        assertNull(PiiMaskingUtil.mask(null));
    }

    @Test
    void mask_noDashPrefix_masksAllButLastFourOfWholeValue() {
        assertEquals("****9999", PiiMaskingUtil.mask("ACC9999"));
    }

    @Test
    void mask_suffixShorterThanFourChars_masksWithoutTruncating() {
        assertEquals("ACC-****1", PiiMaskingUtil.mask("ACC-1"));
    }

    @Test
    void mask_blockedDestinationAccountFromDenylist_masksLastFourDigits() {
        assertEquals("ACC-****9999", PiiMaskingUtil.mask("ACC-9999"));
    }
}
