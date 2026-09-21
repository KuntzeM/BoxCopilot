package com.boxcopilot.backend.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class McpStartupGuardContextTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(McpConfiguration.class, McpStartupGuard.class);

    @Test
    void guardDoesNotExistWhenFlagIsMissing() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(McpStartupGuard.class);
            assertThat(context).doesNotHaveBean(McpProperties.class);
        });
    }

    @Test
    void guardDoesNotExistWhenFlagIsFalseEvenWithoutBackendUrl() {
        runner.withPropertyValues("app.mcp.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(McpStartupGuard.class);
        });
    }

    @Test
    void contextStartsWithCompleteConfiguration() {
        runner.withPropertyValues(
                        "app.mcp.enabled=true",
                        "app.mcp.backend-url=http://localhost:8080",
                        "app.mcp.redirect-uris=https://claude.ai/api/mcp/auth_callback")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(McpProperties.class).redirectUris())
                            .containsExactly("https://claude.ai/api/mcp/auth_callback");
                });
    }

    @Test
    void contextFailsWithoutBackendUrl() {
        runner.withPropertyValues("app.mcp.enabled=true", "app.mcp.redirect-uris=https://claude.ai/cb")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(IllegalStateException.class)
                        .rootCause().hasMessageContaining("BACKEND_URL"));
    }

    @Test
    void contextFailsWithoutRedirectUris() {
        runner.withPropertyValues("app.mcp.enabled=true", "app.mcp.backend-url=http://localhost:8080")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(IllegalStateException.class)
                        .rootCause().hasMessageContaining("redirect"));
    }

    @Test
    void contextFailsWithHttpBackendUrlInProd() {
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .withPropertyValues(
                        "app.mcp.enabled=true",
                        "app.mcp.backend-url=http://boxpilot.example.com",
                        "app.mcp.redirect-uris=https://claude.ai/cb")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(IllegalStateException.class)
                        .rootCause().hasMessageContaining("https"));
    }

    @Test
    void contextFailsWithTestProfile() {
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("test"))
                .withPropertyValues(
                        "app.mcp.enabled=true",
                        "app.mcp.backend-url=http://localhost:8080",
                        "app.mcp.redirect-uris=https://claude.ai/cb")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(IllegalStateException.class)
                        .rootCause().hasMessageContaining("test"));
    }
}
