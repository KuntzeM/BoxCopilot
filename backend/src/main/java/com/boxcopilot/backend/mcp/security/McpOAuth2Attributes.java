package com.boxcopilot.backend.mcp.security;

/** The names BoxPilot adds to the Spring Authorization Server model, and the single scope it issues. */
public final class McpOAuth2Attributes {

    /**
     * Client setting that binds a registered client to exactly one BoxPilot user. The value is the internal user
     * id as a string, matching the principal name of every authorization of that client.
     */
    public static final String CLIENT_USER_ID = "boxpilot.user_id";

    /**
     * Authorization attribute holding the epoch second at which the connection was first authorized. Spring
     * Authorization Server copies attributes across refresh-token rotations, so it measures the absolute age of
     * a connection rather than that of the current token.
     *
     * <p>The value is a decimal {@code String}, not a {@code Long}: the Jackson allowlist of the JDBC
     * authorization store writes a number with its type id and then refuses to read {@code java.lang.Long} back,
     * because that class carries no mixin. A {@code String} needs no type id and passes unchanged.
     */
    public static final String STARTED_AT = "boxpilot.started_at";

    /** The only scope BoxPilot issues; the resource server requires the matching {@code SCOPE_} authority. */
    public static final String SCOPE_READ = "mcp:read";

    private McpOAuth2Attributes() {
    }
}
