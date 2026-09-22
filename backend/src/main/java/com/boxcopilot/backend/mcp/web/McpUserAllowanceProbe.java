package com.boxcopilot.backend.mcp.web;

import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.repository.UserRepository;
import com.boxcopilot.backend.service.McpAllowanceProbe;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Reports the stored MCP allowance of a user. The feature flag is covered by the condition on this bean: with MCP
 * switched off this implementation does not exist and the neutral fallback answers {@code false}.
 */
@Component
@ConditionalOnProperty(prefix = "app.mcp", name = "enabled", havingValue = "true")
class McpUserAllowanceProbe implements McpAllowanceProbe {

    private final UserRepository userRepository;

    McpUserAllowanceProbe(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Deliberately narrower than {@code McpAccessPolicy}, which additionally denies a temporarily locked
     * account: this answer only decides whether the SPA offers the "Claude-Verbindung" dialog at all, and a
     * lock is a passing state that expires by itself. Hiding the dialog while it lasts would also hide the
     * actions that stay legitimate for a locked user — revoking existing connections in particular, which
     * {@code McpAccessPolicy#requireUserAllowedToRevoke} tolerates a lock for on purpose. Enforcement stays with the policy, so a locked user who tries anyway is
     * refused there; they merely see a bare {@code 403} rather than an explanation (documented as an open point
     * in {@code docs/ops/mcp-server.md}).
     */
    @Override
    public boolean isAllowed(Long userId) {
        return userId != null && userRepository.findById(userId).map(User::isMcpAllowed).orElse(false);
    }
}
