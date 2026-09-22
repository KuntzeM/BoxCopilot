package com.boxcopilot.backend.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpStartupGuardTest {

    private static final List<String> REDIRECT_URIS = List.of("https://claude.ai/api/mcp/auth_callback");

    private static McpStartupGuard guard(String backendUrl, List<String> redirectUris, String... profiles) {
        return guard(new McpProperties(true, backendUrl, redirectUris, null, null), new MockEnvironment(), profiles);
    }

    private static McpStartupGuard guard(McpProperties properties, MockEnvironment environment,
                                         String... profiles) {
        environment.setActiveProfiles(profiles);
        return new McpStartupGuard(properties, environment);
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
        assertThat(new McpProperties(true, "http://x", null, null, null).redirectUris()).isEmpty();
    }

    @Test
    void aSingleTrailingSlashIsRemovedFromTheBackendUrl() {
        assertThat(new McpProperties(true, "https://boxpilot.example.com/", List.of(), null, null).backendUrl())
                .isEqualTo("https://boxpilot.example.com");
        assertThat(new McpProperties(true, "https://boxpilot.example.com", List.of(), null, null).backendUrl())
                .isEqualTo("https://boxpilot.example.com");
    }

    @Test
    void acceptsTheDefaultTransportEndpoint() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(McpEndpoints.ENDPOINT_PROPERTY, McpEndpoints.MCP);

        assertThatCode(guard(new McpProperties(true, "http://localhost:8080", REDIRECT_URIS, null, null), environment,
                "dev")::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void rejectsATransportEndpointOtherThanTheProtectedPath() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(McpEndpoints.ENDPOINT_PROPERTY, "/mcp-server");

        assertThatThrownBy(guard(new McpProperties(true, "http://localhost:8080", REDIRECT_URIS, null, null), environment,
                "dev")::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(McpEndpoints.ENDPOINT_PROPERTY);
    }

    @Test
    void rejectsZeroOrNegativeMaxConnectionAge() {
        for (Duration age : List.of(Duration.ZERO, Duration.ofDays(-1))) {
            assertThatThrownBy(guard(new McpProperties(true, "http://localhost:8080", REDIRECT_URIS, age, null),
                    new MockEnvironment(), "dev")::afterPropertiesSet)
                    .as("%s", age)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("app.mcp.max-connection-age");
        }
    }

    @Test
    void acceptsTheStatelessTransportProtocol() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(McpEndpoints.PROTOCOL_PROPERTY, "STATELESS");

        assertThatCode(guard(new McpProperties(true, "http://localhost:8080", REDIRECT_URIS, null, null), environment,
                "dev")::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void rejectsATransportProtocolOtherThanStateless() {
        for (String protocol : List.of("SSE", "STREAMABLE", "stateful")) {
            MockEnvironment environment = new MockEnvironment()
                    .withProperty(McpEndpoints.PROTOCOL_PROPERTY, protocol);

            assertThatThrownBy(guard(new McpProperties(true, "http://localhost:8080", REDIRECT_URIS, null, null),
                    environment, "dev")::afterPropertiesSet)
                    .as("%s", protocol)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(McpEndpoints.PROTOCOL_PROPERTY);
        }
    }

    @Test
    void rejectsConfiguredSessionTransportEndpoints() {
        for (String property : McpEndpoints.SESSION_TRANSPORT_PROPERTIES) {
            MockEnvironment environment = new MockEnvironment().withProperty(property, "/sse");

            assertThatThrownBy(guard(new McpProperties(true, "http://localhost:8080", REDIRECT_URIS, null, null),
                    environment, "dev")::afterPropertiesSet)
                    .as("%s", property)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(property);
        }
    }

    @Test
    void maxConnectionAgeDefaultsToFourteenDays() {
        assertThat(new McpProperties(true, "https://x", List.of(), null, null).maxConnectionAge())
                .isEqualTo(Duration.ofHours(336));
    }
}
