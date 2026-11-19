package com.boxcopilot.backend.security;

/** Neutralises CR/LF and other control characters in user-controlled text before it reaches a log line. */
public final class LogSanitizer {

    private LogSanitizer() {
    }

    public static String clean(Object value) {
        return value == null ? null : value.toString().replaceAll("[\\p{Cntrl}\\u2028\\u2029]", "_");
    }
}
