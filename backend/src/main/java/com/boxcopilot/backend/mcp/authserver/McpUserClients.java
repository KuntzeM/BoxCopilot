package com.boxcopilot.backend.mcp.authserver;

import com.boxcopilot.backend.mcp.security.McpOAuth2Attributes;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Finds and deletes the registered client of one user.
 *
 * <p>{@code RegisteredClientRepository} can look a client up by its id only, and a client is bound to a user by
 * the {@code boxpilot.user_id} entry of its serialised {@code client_settings}. The row is therefore pre-selected
 * with a parameterised {@code LIKE} on that JSON fragment and then loaded through the repository, whose parsed
 * settings decide whether the client really belongs to the user - the {@code LIKE} narrows, it never decides.
 * Deletion goes straight to SQL because the repository offers none; the authorizations of the client follow
 * through {@code ON DELETE CASCADE}.
 *
 * <p>A separate lookup table would be the alternative. It is not worth its migration and its second source of
 * truth: a user holds at most one client, the binding already lives in {@code client_settings}, and every other
 * part of the feature (the access policy, the binding validator) reads it from exactly there.
 */
@Component
@ConditionalOnProperty(prefix = "app.mcp", name = "enabled", havingValue = "true")
public class McpUserClients {

    /** {@code !} escapes the {@code _} of the setting name, which {@code LIKE} would otherwise read as "any". */
    static final String LIKE_ESCAPE = "!";

    static final String SELECT_IDS_BY_BINDING = "SELECT id FROM oauth2_registered_client "
            + "WHERE client_settings LIKE ? ESCAPE '" + LIKE_ESCAPE + "' ORDER BY client_id_issued_at, id";

    static final String DELETE_BY_ID = "DELETE FROM oauth2_registered_client WHERE id = ?";

    private final JdbcTemplate jdbcTemplate;
    private final RegisteredClientRepository registeredClientRepository;

    public McpUserClients(JdbcTemplate jdbcTemplate, RegisteredClientRepository registeredClientRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.registeredClientRepository = registeredClientRepository;
    }

    /** @return the client of this user, or empty if the user has none */
    public Optional<RegisteredClient> findByUserId(long userId) {
        return clientsOf(userId).stream().findFirst();
    }

    /**
     * Deletes every client of this user, and with it every authorization of those clients.
     *
     * @return the number of deleted clients
     */
    public int deleteByUserId(long userId) {
        int deleted = 0;
        for (RegisteredClient client : clientsOf(userId)) {
            deleted += jdbcTemplate.update(DELETE_BY_ID, client.getId());
        }
        return deleted;
    }

    private List<RegisteredClient> clientsOf(long userId) {
        return jdbcTemplate.queryForList(SELECT_IDS_BY_BINDING, String.class, bindingPattern(userId)).stream()
                .map(registeredClientRepository::findById)
                .filter(client -> client != null && isBoundTo(client, userId))
                .toList();
    }

    private static boolean isBoundTo(RegisteredClient client, long userId) {
        return McpOAuth2Attributes.boundUserId(client.getClientSettings())
                .filter(bound -> bound == userId)
                .isPresent();
    }

    /**
     * The settings are stored as a JSON object whose values carry no whitespace, so the binding of a user always
     * appears as one contiguous fragment. The closing quote keeps user 42 from matching user 421, and the
     * wildcards of the fragment itself are escaped so that the pre-select stays as narrow as it reads.
     */
    static String bindingPattern(long userId) {
        return "%" + escapeLike("\"" + McpOAuth2Attributes.CLIENT_USER_ID + "\":\"" + userId + "\"") + "%";
    }

    private static String escapeLike(String literal) {
        StringBuilder escaped = new StringBuilder(literal.length());
        for (char c : literal.toCharArray()) {
            if (c == '%' || c == '_' || c == LIKE_ESCAPE.charAt(0)) {
                escaped.append(LIKE_ESCAPE);
            }
            escaped.append(c);
        }
        return escaped.toString();
    }
}
