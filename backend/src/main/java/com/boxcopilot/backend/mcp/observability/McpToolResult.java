package com.boxcopilot.backend.mcp.observability;

/** The result classes a tool call is counted and audited under; the specification names exactly these four. */
public enum McpToolResult {

    /** The tool ran and answered. */
    OK("ok"),

    /** Access was refused: no user in the context, or the gate turned the user away. */
    DENIED("denied"),

    /** A rate limit was reached before the tool ran. */
    RATE_LIMITED("rate_limited"),

    /** The tool rejected its input or failed while running. */
    ERROR("error");

    private final String tag;

    McpToolResult(String tag) {
        this.tag = tag;
    }

    /** The stable, lower-case name used in the metric tag and in the audit line. */
    public String tag() {
        return tag;
    }
}
