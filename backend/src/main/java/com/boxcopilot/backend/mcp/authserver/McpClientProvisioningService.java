package com.boxcopilot.backend.mcp.authserver;

import com.boxcopilot.backend.mcp.McpProperties;
import com.boxcopilot.backend.mcp.McpUserGate;
import com.boxcopilot.backend.mcp.security.McpAuthorizationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The lifecycle of the one MCP client a user may hold: created lazily on first use, replaced on request, and
 * removed - with every connection - when the user loses MCP access.
 *
 * <p>Every operation a user triggers passes {@link McpUserGate}, which reloads the user and refuses a disabled,
 * locked, unknown or not-allowed one. {@link #revokeAllAccess(long)} is the exception: it runs while access is
 * being withdrawn, so the gate would refuse exactly the teardown that has to happen.
 *
 * <p>Nothing here logs a client id. It is the credential of a public client, and the specification forbids it in
 * logs; a log line names the user and what happened.
 */
@Service
@ConditionalOnProperty(prefix = "app.mcp", name = "enabled", havingValue = "true")
public class McpClientProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(McpClientProvisioningService.class);

    private final McpUserClients userClients;
    private final RegisteredClientRepository registeredClientRepository;
    private final McpAuthorizationRegistry authorizations;
    private final McpUserGate userGate;
    private final McpClientIdGenerator clientIdGenerator;
    private final List<String> redirectUris;

    public McpClientProvisioningService(McpUserClients userClients,
                                        RegisteredClientRepository registeredClientRepository,
                                        McpAuthorizationRegistry authorizations, McpUserGate userGate,
                                        McpClientIdGenerator clientIdGenerator, McpProperties properties) {
        this.userClients = userClients;
        this.registeredClientRepository = registeredClientRepository;
        this.authorizations = authorizations;
        this.userGate = userGate;
        this.clientIdGenerator = clientIdGenerator;
        this.redirectUris = properties.redirectUris();
    }

    /**
     * Returns the client id of the user, creating the client on first use.
     *
     * @return the client id, unchanged on every further call
     */
    @Transactional
    public String provision(long userId) {
        userGate.requireActiveUser(userId);
        return userClients.findByUserId(userId)
                .map(RegisteredClient::getClientId)
                .orElseGet(() -> create(userId));
    }

    /**
     * Replaces the client of the user. The old client and every connection of the user are gone when this
     * returns, so a client id that leaked stops working.
     *
     * @return the new client id
     */
    @Transactional
    public String regenerate(long userId) {
        userGate.requireActiveUser(userId);
        int connections = removeConnectionsAndClient(userId);
        String clientId = create(userId);
        log.info("MCP client regenerated: userId={} revokedConnections={}", userId, connections);
        return clientId;
    }

    /** Ends every connection of the user; the client itself stays and can be connected again. */
    @Transactional
    public void revokeConnections(long userId) {
        userGate.requireActiveUser(userId);
        int connections = authorizations.removeByPrincipal(String.valueOf(userId));
        log.info("MCP connections revoked: userId={} revokedConnections={}", userId, connections);
    }

    /**
     * Removes the client of the user and every connection of it. Used when MCP access is withdrawn, the account
     * is disabled or deleted; it deliberately does not consult the gate, which would refuse by then.
     */
    @Transactional
    public void revokeAllAccess(long userId) {
        int connections = removeConnectionsAndClient(userId);
        log.info("MCP access revoked: userId={} revokedConnections={}", userId, connections);
    }

    /**
     * Deleting the client cascades to its authorizations; deleting by principal first also catches an
     * authorization that outlived its client, and both cover the same rows because a user holds one client.
     *
     * @return the number of deleted authorizations
     */
    private int removeConnectionsAndClient(long userId) {
        int connections = authorizations.removeByPrincipal(String.valueOf(userId));
        userClients.deleteByUserId(userId);
        return connections;
    }

    private String create(long userId) {
        RegisteredClient client = McpRegisteredClients.publicClient(userId, clientIdGenerator.newClientId(),
                redirectUris);
        registeredClientRepository.save(client);
        log.info("MCP client created: userId={}", userId);
        return client.getClientId();
    }
}
