package com.boxcopilot.backend.mcp.abuse;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The client address BoxPilot counts and audits by. It is whatever the servlet container resolved, which behind
 * the reverse proxy is the real client only because forwarded headers are honoured for internal proxies alone
 * (M4). The headers are deliberately not read here: doing so would trust every caller again.
 */
public final class McpClientAddress {

    /** Stands in for a missing address, so a counter key and an audit field are never empty. */
    public static final String UNKNOWN = "unknown";

    private McpClientAddress() {
    }

    public static String of(HttpServletRequest request) {
        String address = request == null ? null : request.getRemoteAddr();
        return address == null || address.isBlank() ? UNKNOWN : address;
    }
}
