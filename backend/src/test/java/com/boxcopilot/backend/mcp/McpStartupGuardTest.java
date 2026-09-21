package com.boxcopilot.backend.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpStartupGuardTest {

    private static final List<String> REDIRECT_URIS = List.of("https://claude.ai/api/mcp/auth_callback");

    private static McpStartupGuard guard(String backendUrl, List<String> redirectUris, String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return new McpStartupGuard(new McpProperties(true, backendUrl, redirectUris), environment);
    }

    @Test
    void acceptsCompleteConfiguration() {
        assertThatCode(guard("http://localhost:8080", REDIRECT_URIS, "dev")::afterPropertiesSet)
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsHttpsBackendUrlInProd() {
        assertThatCode(guard("https://boxpilot.example.com", REDIRECT_URIS, "prod")::afterPropertiesSet)
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsBlankBackendUrl() {
        assertThatThrownBy(guard("  ", REDIRECT_URIS, "dev")::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BACKEND_URL");
    }

    @Test
    void rejectsMissingBackendUrl() {
        assertThatThrownBy(guard(null, REDIRECT_URIS, "dev")::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BACKEND_URL");
    }

    @Test
    void rejectsNonHttpsBackendUrlInProd() {
        assertThatThrownBy(guard("http://boxpilot.example.com", REDIRECT_URIS, "prod")::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("https");
    }

    @Test
    void rejectsEmptyRedirectUris() {
        assertThatThrownBy(guard("http://localhost:8080", List.of(), "dev")::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("redirect");
    }

    @Test
    void rejectsBlankRedirectUri() {
        assertThatThrownBy(guard("http://localhost:8080", List.of("https://claude.ai/cb", " "), "dev")::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("redirect");
    }

    @Test
    void rejectsTestProfile() {
        assertThatThrownBy(guard("http://localhost:8080", REDIRECT_URIS, "test")::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test");
    }

    @Test
    void missingRedirectUrisBecomeEmptyList() {
        assertThat(new McpProperties(true, "http://x", null).redirectUris()).isEmpty();
    }
}
