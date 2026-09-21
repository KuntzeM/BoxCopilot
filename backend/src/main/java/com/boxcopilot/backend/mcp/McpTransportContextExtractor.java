package com.boxcopilot.backend.mcp;

import io.modelcontextprotocol.common.McpTransportContext;
import org.springframework.web.servlet.function.ServerRequest;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;

/**
 * Copies the user id of the authenticated principal into the {@link McpTransportContext}. It runs in the request
 * thread, so the principal is still available. Anything but a positive numeric principal name yields a context
 * without user id, which the tools treat as access denied.
 */
class McpTransportContextExtractor
        implements io.modelcontextprotocol.server.McpTransportContextExtractor<ServerRequest> {

    @Override
    public McpTransportContext extract(ServerRequest request) {
        return request.principal()
                .map(Principal::getName)
                .flatMap(McpTransportContextExtractor::parseUserId)
                .map(userId -> McpTransportContext.create(Map.of(McpTransportContextKeys.USER_ID, userId)))
                .orElse(McpTransportContext.EMPTY);
    }

    private static Optional<Long> parseUserId(String name) {
        if (name == null) {
            return Optional.empty();
        }
        try {
            long userId = Long.parseLong(name);
            return userId > 0 ? Optional.of(userId) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
