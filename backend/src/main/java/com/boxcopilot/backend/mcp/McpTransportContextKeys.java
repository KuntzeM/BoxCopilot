package com.boxcopilot.backend.mcp;

/** Keys of the values the transport puts into the {@code McpTransportContext}. */
public final class McpTransportContextKeys {

    /** Internal user id ({@link Long}) taken from the authenticated principal; absent if there is none. */
    public static final String USER_ID = "boxpilot.user_id";

    private McpTransportContextKeys() {
    }
}
