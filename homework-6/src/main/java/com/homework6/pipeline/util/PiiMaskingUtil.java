package com.homework6.pipeline.util;

/**
 * Masks account references before they reach a log line. Per agents.md, account numbers
 * must never appear in plaintext in logs — only the last 4 characters are shown.
 */
public final class PiiMaskingUtil {

    private static final int VISIBLE_SUFFIX_LENGTH = 4;

    private PiiMaskingUtil() {
    }

    /**
     * "ACC-1001" -> "ACC-****1001". Values without a recognizable prefix are masked in full
     * except for the trailing {@value #VISIBLE_SUFFIX_LENGTH} characters.
     */
    public static String mask(String accountRef) {
        if (accountRef == null) {
            return null;
        }
        int dashIdx = accountRef.lastIndexOf('-');
        String prefix = dashIdx >= 0 ? accountRef.substring(0, dashIdx + 1) : "";
        String suffixSource = dashIdx >= 0 ? accountRef.substring(dashIdx + 1) : accountRef;

        if (suffixSource.length() <= VISIBLE_SUFFIX_LENGTH) {
            return prefix + "****" + suffixSource;
        }
        String visible = suffixSource.substring(suffixSource.length() - VISIBLE_SUFFIX_LENGTH);
        return prefix + "****" + visible;
    }
}
