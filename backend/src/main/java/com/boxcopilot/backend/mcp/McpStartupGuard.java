package com.boxcopilot.backend.mcp;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Aborts startup when MCP is enabled with an unsafe or incomplete configuration. It only exists when
 * {@code app.mcp.enabled=true}.
 */
@Component
@ConditionalOnProperty(prefix = "app.mcp", name = "enabled", havingValue = "true")
class McpStartupGuard implements InitializingBean {

    private static final String TEST_PROFILE = "test";
    private static final String PROD_PROFILE = "prod";

    private final McpProperties properties;
    private final Environment environment;

    McpStartupGuard(McpProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        if (environment.acceptsProfiles(Profiles.of(TEST_PROFILE))) {
            throw new IllegalStateException(
                    "app.mcp.enabled=true is not allowed with the '" + TEST_PROFILE + "' profile");
        }
        String backendUrl = properties.backendUrl();
        if (backendUrl == null || backendUrl.isBlank()) {
            throw new IllegalStateException("app.mcp.enabled=true requires BACKEND_URL (app.mcp.backend-url)");
        }
        if (environment.acceptsProfiles(Profiles.of(PROD_PROFILE)) && !backendUrl.startsWith("https://")) {
            throw new IllegalStateException(
                    "app.mcp.backend-url must start with https:// in the '" + PROD_PROFILE + "' profile");
        }
        if (properties.redirectUris().isEmpty() || properties.redirectUris().stream().anyMatch(String::isBlank)) {
            throw new IllegalStateException(
                    "app.mcp.enabled=true requires at least one non-blank redirect URI (app.mcp.redirect-uris)");
        }
    }
}
