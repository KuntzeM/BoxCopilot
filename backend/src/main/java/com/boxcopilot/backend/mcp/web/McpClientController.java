package com.boxcopilot.backend.mcp.web;

import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.mcp.McpAccessDeniedException;
import com.boxcopilot.backend.mcp.authserver.McpClientProvisioningService;
import com.boxcopilot.backend.mcp.authserver.McpSessionUserResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The "Claude connection" endpoints of the current user. They sit on the application chain, so they need a
 * session and a CSRF token like every other {@code /api/v1} endpoint; with the feature switched off the
 * controller does not exist and the paths answer 404.
 *
 * <p>The user is taken from the session and from nowhere else: there is no parameter, no path variable and no
 * header that could name another account, so a caller can only ever change their own client.
 */
@RestController
@RequestMapping("/api/v1/me")
@ConditionalOnProperty(prefix = "app.mcp", name = "enabled", havingValue = "true")
class McpClientController {

    private static final Logger log = LoggerFactory.getLogger(McpClientController.class);

    private final McpClientProvisioningService provisioning;
    private final McpSessionUserResolver sessionUserResolver;

    McpClientController(McpClientProvisioningService provisioning, McpSessionUserResolver sessionUserResolver) {
        this.provisioning = provisioning;
        this.sessionUserResolver = sessionUserResolver;
    }

    /** @return the client id of the caller; the client is created on the first call and then stays the same */
    @PostMapping("/mcp-client")
    McpClientResponse client(Authentication authentication) {
        return new McpClientResponse(provisioning.provision(userId(authentication)));
    }

    /** @return the new client id; the previous client and every connection of the caller are gone */
    @PostMapping("/mcp-client/regenerate")
    McpClientResponse regenerate(Authentication authentication) {
        return new McpClientResponse(provisioning.regenerate(userId(authentication)));
    }

    /** Ends every MCP connection of the caller and keeps the client. */
    @DeleteMapping("/mcp-connections")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeConnections(Authentication authentication) {
        provisioning.revokeConnections(userId(authentication));
    }

    /**
     * A user who may not use MCP - unknown, disabled, locked or not allowed - gets a bare 403. The reason stays
     * in the log; the response says nothing about the state of the account.
     */
    @ExceptionHandler(McpAccessDeniedException.class)
    ResponseEntity<Void> handleAccessDenied(McpAccessDeniedException e) {
        log.debug("MCP client request denied: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    private long userId(Authentication authentication) {
        return sessionUserResolver.resolve(authentication)
                .map(User::getId)
                .orElseThrow(() -> new McpAccessDeniedException("no BoxPilot account behind this session"));
    }

    /**
     * The response body of both provisioning endpoints.
     *
     * <p>Its {@code toString} hides the id: Spring MVC writes the body of a response into the log as soon as
     * {@code org.springframework.web} is set to DEBUG, and the client id is a credential that must not appear
     * there. Serialisation is unaffected, because Jackson reads the record component.
     */
    record McpClientResponse(String clientId) {

        @Override
        public String toString() {
            return "McpClientResponse[clientId=***]";
        }
    }
}
