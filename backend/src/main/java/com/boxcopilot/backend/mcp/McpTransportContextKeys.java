package com.boxcopilot.backend.mcp;

/** Keys of the values the transport puts into the {@code McpTransportContext}. */
public final class McpTransportContextKeys {

    /** Internal user id ({@link Long}) taken from the authenticated principal; absent if there is none. */
    public static final String USER_ID = "boxpilot.user_id";

    /**
     * Client address ({@link String}) of the request the call arrived with, for the audit log. It is always
     * present, standing in for an unresolvable address with a fixed placeholder.
     */
    public static final String CLIENT_ADDRESS = "boxpilot.client_address";

    private McpTransportContextKeys() {
    }
}
