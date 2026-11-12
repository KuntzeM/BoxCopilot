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

    /** Enabled MCP properties; the rate limits and the cleanup cron fall back to their documented defaults. */
    private static McpProperties properties(String backendUrl, List<String> redirectUris, Duration maxAge) {
        return new McpProperties(true, backendUrl, redirectUris, maxAge, null, null, null);
    }

    private static McpStartupGuard guard(String backendUrl, List<String> redirectUris, String... profiles) {
        return guard(properties(backendUrl, redirectUris, null), new MockEnvironment(), profiles);
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
        assertThat(properties("http://x", null, null).redirectUris()).isEmpty();
    }

    @Test
    void aSingleTrailingSlashIsRemovedFromTheBackendUrl() {
        assertThat(properties("https://boxpilot.example.com/", List.of(), null).backendUrl())
                .isEqualTo("https://boxpilot.example.com");
        assertThat(properties("https://boxpilot.example.com", List.of(), null).backendUrl())
                .isEqualTo("https://boxpilot.example.com");
    }

    @Test
    void acceptsTheDefaultTransportEndpoint() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(McpEndpoints.ENDPOINT_PROPERTY, McpEndpoints.MCP);

        assertThatCode(guard(properties("http://localhost:8080", REDIRECT_URIS, null), environment,
                "dev")::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void rejectsATransportEndpointOtherThanTheProtectedPath() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(McpEndpoints.ENDPOINT_PROPERTY, "/mcp-server");

        assertThatThrownBy(guard(properties("http://localhost:8080", REDIRECT_URIS, null), environment,
                "dev")::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(McpEndpoints.ENDPOINT_PROPERTY);
    }

    @Test
    void rejectsZeroOrNegativeMaxConnectionAge() {
        for (Duration age : List.of(Duration.ZERO, Duration.ofDays(-1))) {
            assertThatThrownBy(guard(properties("http://localhost:8080", REDIRECT_URIS, age),
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

        assertThatCode(guard(properties("http://localhost:8080", REDIRECT_URIS, null), environment,
                "dev")::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void rejectsATransportProtocolOtherThanStateless() {
        for (String protocol : List.of("SSE", "STREAMABLE", "stateful")) {
            MockEnvironment environment = new MockEnvironment()
                    .withProperty(McpEndpoints.PROTOCOL_PROPERTY, protocol);

            assertThatThrownBy(guard(properties("http://localhost:8080", REDIRECT_URIS, null),
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

            assertThatThrownBy(guard(properties("http://localhost:8080", REDIRECT_URIS, null),
                    environment, "dev")::afterPropertiesSet)
                    .as("%s", property)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(property);
        }
    }

    @Test
    void maxConnectionAgeDefaultsToFourteenDays() {
        assertThat(properties("https://x", List.of(), null).maxConnectionAge())
                .isEqualTo(Duration.ofHours(336));
    }

    /** The specification's starting values; they are also spelled out in {@code application.yml}. */
    @Test
    void theRateLimitsDefaultToTheSpecifiedStartingValues() {
        McpProperties.RateLimit rateLimit = properties("https://x", List.of(), null).rateLimit();

        assertThat(rateLimit.callsPerMinute()).isEqualTo(60);
        assertThat(rateLimit.imageCallsPerDay()).isEqualTo(200);
        assertThat(rateLimit.authorizationRequestsPerMinute()).isEqualTo(30);
        assertThat(rateLimit.failedTokenLookupsPerMinute()).isEqualTo(30);
    }

    /** A limit of zero would switch the protection off rather than tighten it, so it falls back too. */
    @Test
    void aNonPositiveRateLimitFallsBackToItsDefault() {
        assertThat(new McpProperties.RateLimit(-1, 0, -30, 0))
                .isEqualTo(new McpProperties.RateLimit(60, 200, 30, 30));
    }

    private static McpStartupGuard guardWithSpa(String backendUrl, String spaUrl) {
        McpProperties p = new McpProperties(true, backendUrl, REDIRECT_URIS, null, null, null, null, spaUrl);
        return guard(p, new MockEnvironment(), "dev");
    }

    @Test
    void acceptsSameOriginIgnoringDefaultPortPathAndCase() {
        assertThatCode(guardWithSpa("https://Box.example.com", "https://box.example.com:443/api/v1")::afterPropertiesSet)
                .doesNotThrowAnyException();
        assertThatCode(guardWithSpa("http://localhost:8080/", "http://localhost:8080")::afterPropertiesSet)
                .doesNotThrowAnyException();
    }

    @Test
    void skipsTheOriginCheckWhenTheSpaUrlIsBlank() {
        assertThatCode(guardWithSpa("https://box.example.com", "  ")::afterPropertiesSet).doesNotThrowAnyException();
        assertThatCode(guardWithSpa("https://box.example.com", null)::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void rejectsDifferentHostOrPortNamingBothValues() {
        assertThatThrownBy(guardWithSpa("https://box.example.com", "https://other.example.com")::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VITE_API_BASE_URL (https://other.example.com)")
                .hasMessageContaining("BACKEND_URL (https://box.example.com)");
        assertThatThrownBy(guardWithSpa("https://box.example.com", "https://box.example.com:8443")::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theCleanupCronDefaultsToNightlyAtThree() {
        assertThat(properties("https://x", List.of(), null).cleanupCron()).isEqualTo("0 0 3 * * *");
        assertThat(new McpProperties(true, "https://x", List.of(), null, null, null, "  ").cleanupCron())
                .isEqualTo("0 0 3 * * *");
    }
}
