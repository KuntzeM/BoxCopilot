package com.boxcopilot.backend.mcp;

import io.modelcontextprotocol.common.McpTransportContext;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.web.servlet.function.ServerRequest;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;

/**
 * Copies the user id of the authenticated principal into the {@link McpTransportContext}. It runs in the
 * request thread, so the principal is still available.
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
        return request.principal()
                .flatMap(McpTransportContextExtractor::bearerTokenSubject)
                .flatMap(McpTransportContextExtractor::parseUserId)
                .map(userId -> McpTransportContext.create(Map.of(McpTransportContextKeys.USER_ID, userId)))
                .orElse(McpTransportContext.EMPTY);
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
