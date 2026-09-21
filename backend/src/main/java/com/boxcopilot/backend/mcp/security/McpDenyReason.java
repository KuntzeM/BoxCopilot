package com.boxcopilot.backend.mcp.security;

/** Why {@link McpAccessPolicy} refused. Only the code is logged, never a token, a client id or a token value. */
enum McpDenyReason {

    /** No authorization was found for the presented token. */
    NO_AUTHORIZATION,
    /** The principal of the authorization is not an internal user id. */
    PRINCIPAL_NOT_A_USER_ID,
    /** The client is unknown or bound to a different user than the authorization's principal. */
    CLIENT_BINDING,
    /** The authorization carries no start timestamp, so its absolute age cannot be established. */
    NO_START_TIMESTAMP,
    /** The start timestamp lies further in the future than the tolerated clock difference. */
    START_TIMESTAMP_IN_FUTURE,
    /** The connection has exceeded the configured absolute age; re-consent is required. */
    CONNECTION_TOO_OLD,
    /** No user with this id exists any more. */
    UNKNOWN_USER,
    /** The user account is disabled. */
    USER_DISABLED,
    /** The user account is temporarily locked; nothing is deleted for this reason. */
    USER_LOCKED,
    /** An administrator has not granted (or has withdrawn) MCP access for this user. */
    MCP_NOT_ALLOWED,
    /** The decision could not be made; fail-closed. */
    ERROR
}
