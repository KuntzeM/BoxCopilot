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
        return new McpStartupGuard(new McpProperties(true, backendUrl, redirectUris, null), environment);
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
    void rejectsBackendUrlWithoutHttpScheme() {
        assertThatThrownBy(guard("ftp://boxpilot.example.com", REDIRECT_URIS, "dev")::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.mcp.backend-url");
    }

    @Test
    void rejectsUnparseableBackendUrl() {
        assertThatThrownBy(guard("http://exa mple.com", REDIRECT_URIS, "dev")::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.mcp.backend-url");
    }

    @Test
    void rejectsBackendUrlWithoutHost() {
        assertThatThrownBy(guard("http://", REDIRECT_URIS, "dev")::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.mcp.backend-url");
    }

    @Test
    void rejectsBackendUrlWithQueryOrFragment() {
        assertThatThrownBy(guard("http://localhost:8080/?a=b", REDIRECT_URIS, "dev")::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.mcp.backend-url");
    }

    @Test
    void missingRedirectUrisBecomeEmptyList() {
        assertThat(new McpProperties(true, "http://x", null, null).redirectUris()).isEmpty();
    }

    @Test
    void aSingleTrailingSlashIsRemovedFromTheBackendUrl() {
        assertThat(new McpProperties(true, "https://boxpilot.example.com/", List.of(), null).backendUrl())
                .isEqualTo("https://boxpilot.example.com");
        assertThat(new McpProperties(true, "https://boxpilot.example.com", List.of(), null).backendUrl())
                .isEqualTo("https://boxpilot.example.com");
    }

    @Test
    void maxConnectionAgeDefaultsToFourteenDays() {
        assertThat(new McpProperties(true, "https://x", List.of(), null).maxConnectionAge())
                .isEqualTo(java.time.Duration.ofHours(336));
    }
}
