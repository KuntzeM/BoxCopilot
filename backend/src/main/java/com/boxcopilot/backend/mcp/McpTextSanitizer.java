package com.boxcopilot.backend.mcp;

/**
 * Neutralises untrusted free text (item names, box descriptions) before it leaves the server through MCP.
 * Invisible and direction-changing characters are removed so that hidden instructions cannot be smuggled
 * into a model context, whitespace is normalised to single spaces and the length is bounded.
 */
public final class McpTextSanitizer {

    private static final char SPACE = ' ';

    private McpTextSanitizer() {
    }

    /**
     * @param text   raw text, may be {@code null}
     * @param maxLen maximum length of the result in UTF-16 code units, never splits a surrogate pair
     * @return sanitised, trimmed text; empty for {@code null}
     */
    public static String clean(String text, int maxLen) {
        if (maxLen < 0) {
            throw new IllegalArgumentException("maxLen must not be negative");
        }
        if (text == null) {
            return "";
        }
        StringBuilder cleaned = new StringBuilder(Math.min(text.length(), maxLen + 1));
        text.codePoints().forEach(codePoint -> append(cleaned, codePoint));
        return truncate(cleaned, maxLen);
    }

    private static void append(StringBuilder target, int codePoint) {
        if (isInvisible(codePoint)) {
            return;
        }
        if (isWhitespace(codePoint)) {
            if (!target.isEmpty() && target.charAt(target.length() - 1) != SPACE) {
                target.append(SPACE);
            }
            return;
        }
        target.appendCodePoint(codePoint);
    }

    private static boolean isWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    private static boolean isInvisible(int codePoint) {
        if (isWhitespace(codePoint)) {
            return false;
        }
        return Character.getType(codePoint) == Character.CONTROL
                || Character.getType(codePoint) == Character.SURROGATE
                || isZeroWidth(codePoint)
                || isBidiControl(codePoint);
    }

    private static boolean isZeroWidth(int codePoint) {
        return (codePoint >= 0x200B && codePoint <= 0x200F)
                || (codePoint >= 0x2060 && codePoint <= 0x2064)
                || codePoint == 0xFEFF;
    }

    private static boolean isBidiControl(int codePoint) {
        return (codePoint >= 0x202A && codePoint <= 0x202E)
                || (codePoint >= 0x2066 && codePoint <= 0x2069)
                || codePoint == 0x061C;
    }

    private static String truncate(StringBuilder cleaned, int maxLen) {
        int end = cleaned.length();
        if (end > maxLen) {
            end = maxLen;
            if (end > 0 && Character.isHighSurrogate(cleaned.charAt(end - 1))) {
                end--;
            }
        }
        while (end > 0 && cleaned.charAt(end - 1) == SPACE) {
            end--;
        }
        return cleaned.substring(0, end);
    }
}
