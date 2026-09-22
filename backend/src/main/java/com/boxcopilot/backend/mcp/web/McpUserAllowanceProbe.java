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

    @Override
    public boolean isAllowed(Long userId) {
        return userId != null && userRepository.findById(userId).map(User::isMcpAllowed).orElse(false);
    }
}
