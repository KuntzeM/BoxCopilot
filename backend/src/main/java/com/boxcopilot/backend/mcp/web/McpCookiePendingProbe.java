package com.boxcopilot.backend.mcp.web;

import com.boxcopilot.backend.service.McpPendingProbe;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Reports a pending authorization whenever the request carries the {@code mcp_authz} cookie. */
@Component
@ConditionalOnProperty(prefix = "app.mcp", name = "enabled", havingValue = "true")
class McpCookiePendingProbe implements McpPendingProbe {

    @Override
    public boolean isPending(HttpServletRequest request) {
        return McpPendingCookies.read(request).isPresent();
    }
}
