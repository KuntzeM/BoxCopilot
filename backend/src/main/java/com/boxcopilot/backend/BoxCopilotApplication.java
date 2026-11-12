package com.boxcopilot.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerAutoConfiguration;
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerJwtAutoConfiguration;

/**
 * The authorization server auto-configurations are excluded on purpose. They react to the mere presence of the
 * Spring Authorization Server jar, not to a property, and would contribute an {@code AuthorizationServerSettings}
 * bean and a generated RSA {@code JWKSource} even with {@code app.mcp.enabled=false}. The MCP configuration
 * declares everything it needs itself, behind that flag. The exclusion sits here rather than in
 * {@code spring.autoconfigure.exclude} because a profile-specific YAML list would silently replace it.
 */
@SpringBootApplication(exclude = {
        OAuth2AuthorizationServerAutoConfiguration.class,
        OAuth2AuthorizationServerJwtAutoConfiguration.class})
public class BoxCopilotApplication {
    public static void main(String[] args) {
        SpringApplication.run(BoxCopilotApplication.class, args);
    }
}
