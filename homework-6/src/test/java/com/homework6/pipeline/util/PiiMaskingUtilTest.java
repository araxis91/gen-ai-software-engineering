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
}
