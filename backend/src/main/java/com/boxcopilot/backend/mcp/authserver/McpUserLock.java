package com.boxcopilot.backend.mcp.authserver;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Serialises everything that provisions or revokes the MCP access of one user, by taking the row lock of that
 * user before anything is read or written.
 *
 * <p>Without it, provisioning is a check-then-insert: two concurrent requests could both find no client and
 * both create one, and a request could read {@code mcp_allowed=true} that a revocation running beside it is
 * about to withdraw, leaving an orphaned client behind. Taking the same lock in both paths makes the second one
 * wait and then read the committed result of the first.
 *
 * <p>The lock lives as long as the surrounding transaction, so every caller has to be transactional. It locks
 * the user, not the client, because a user without a client has no row to lock.
 */
@Component
@ConditionalOnProperty(prefix = "app.mcp", name = "enabled", havingValue = "true")
public class McpUserLock {

    static final String LOCK_USER = "SELECT id FROM users WHERE id = ? FOR UPDATE";

    private final JdbcTemplate jdbcTemplate;

    public McpUserLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Waits for, and then holds, the row lock of this user until the current transaction ends. An unknown user
     * has no row and therefore nothing to wait for; the caller's own checks decide what that means.
     */
    public void lock(long userId) {
        jdbcTemplate.queryForList(LOCK_USER, Long.class, userId);
    }
}
