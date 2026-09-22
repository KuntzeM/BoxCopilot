package com.boxcopilot.backend.mcp.abuse;

/**
 * The limits enforced in the HTTP layer, named so that an audit line and a metric tag can say which one was
 * reached without any free text. The daily {@code get_item_image} quota is not here: it is enforced inside the
 * tool and is therefore recorded by the tool's own audit line and counter.
 */
public enum McpRateLimit {

    /** Calls to {@code /mcp} per token subject. */
    TOOL_CALLS("calls"),

    /** Requests to the authorize and token endpoints per client address. */
    AUTHORIZATION_REQUESTS("authorization_requests"),

    /** Failed bearer-token lookups in the resource server chain, per client address. */
    FAILED_TOKEN_LOOKUPS("failed_token_lookups");

    private final String tag;

    McpRateLimit(String tag) {
        this.tag = tag;
    }

    /** The stable, lower-case name used in the metric tag and in the audit line. */
    public String tag() {
        return tag;
    }
}
