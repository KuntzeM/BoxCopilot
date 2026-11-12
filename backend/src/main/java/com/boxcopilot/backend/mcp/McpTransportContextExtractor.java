package com.boxcopilot.backend.mcp;

import com.boxcopilot.backend.mcp.abuse.McpClientAddress;
import io.modelcontextprotocol.common.McpTransportContext;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.web.servlet.function.ServerRequest;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Copies the user id of the authenticated principal and the client address into the {@link McpTransportContext}.
 * It runs in the request thread, so the principal and the request are still available; the tools themselves see
 * neither. The address is what the audit log records, and it is the one the container resolved, never a header
 * the caller supplied.
 *
 * <p>Only a bearer token issued by this application counts: the principal has to be a
 * {@link BearerTokenAuthentication} carrying an {@link OAuth2AuthenticatedPrincipal}, which is what
 * {@code McpOpaqueTokenIntrospector} produces. A session authentication is deliberately not accepted, because
 * its name is a username and a user whose username happens to be numeric would otherwise be read as somebody
 * else's user id. Anything else yields a context without user id, which the tools treat as access denied.
 */
class McpTransportContextExtractor
        implements io.modelcontextprotocol.server.McpTransportContextExtractor<ServerRequest> {

    @Override
    public McpTransportContext extract(ServerRequest request) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(McpTransportContextKeys.CLIENT_ADDRESS, McpClientAddress.of(request.servletRequest()));
        request.principal()
                .flatMap(McpTransportContextExtractor::bearerTokenSubject)
                .flatMap(McpTransportContextExtractor::parseUserId)
                .ifPresent(userId -> values.put(McpTransportContextKeys.USER_ID, userId));
        return McpTransportContext.create(values);
    }

    private static Optional<String> bearerTokenSubject(Principal principal) {
        if (!(principal instanceof BearerTokenAuthentication bearerToken)
                || !bearerToken.isAuthenticated()
                || !(bearerToken.getPrincipal() instanceof OAuth2AuthenticatedPrincipal tokenPrincipal)) {
            return Optional.empty();
        }
        return Optional.ofNullable(tokenPrincipal.getName());
    }

    private static Optional<Long> parseUserId(String name) {
        try {
            long userId = Long.parseLong(name);
            return userId > 0 ? Optional.of(userId) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
