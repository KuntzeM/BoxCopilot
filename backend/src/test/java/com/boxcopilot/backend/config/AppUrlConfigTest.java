package com.boxcopilot.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * One public origin, {@code APP_URL}, feeds every URL the backend needs: the SPA (redirect target and CORS origin),
 * the OIDC redirect URI and the MCP issuer. The variables it replaced are no longer read.
 */
class AppUrlConfigTest {

    private static final String APP_URL = "https://box.example.com";

    private static StandardEnvironment env(Map<String, Object> operator, String... files) throws IOException {
        StandardEnvironment env = new StandardEnvironment();
        for (String file : files) {
            for (PropertySource<?> yaml : new YamlPropertySourceLoader().load(file, new ClassPathResource(file))) {
                env.getPropertySources().addFirst(yaml);
            }
        }
        env.getPropertySources().addFirst(new MapPropertySource("operator", operator));
        return env;
    }

    private static StandardEnvironment prod(Map<String, Object> operator) throws IOException {
        return env(operator, "application.yml", "application-prod.yml");
    }

    @Test
    void prodDerivesEveryUrlFromAppUrl() throws IOException {
        StandardEnvironment env = prod(Map.of("APP_URL", APP_URL));

        assertThat(env.getProperty("frontend.url")).isEqualTo(APP_URL);
        assertThat(env.getProperty("app.mcp.backend-url")).isEqualTo(APP_URL);
        assertThat(env.getProperty("spring.security.oauth2.client.registration.nextcloud.redirect-uri"))
                .isEqualTo(APP_URL + "/login/oauth2/code/{registrationId}");
    }

    @Test
    void theReplacedVariablesAreIgnored() throws IOException {
        Map<String, Object> operator = new HashMap<>();
        operator.put("APP_URL", APP_URL);
        operator.put("FRONTEND_URL", "https://front.example.com");
        operator.put("BACKEND_URL", "https://api.example.com");
        operator.put("VITE_API_BASE_URL", "https://api.example.com");
        StandardEnvironment env = prod(operator);

        assertThat(env.getProperty("frontend.url")).isEqualTo(APP_URL);
        assertThat(env.getProperty("app.mcp.backend-url")).isEqualTo(APP_URL);
        assertThat(env.getProperty("spring.security.oauth2.client.registration.nextcloud.redirect-uri"))
                .startsWith(APP_URL + "/");
        assertThat(env.containsProperty("app.mcp.spa-api-base-url")).isFalse();
    }

    @Test
    void withoutAppUrlProdLeavesThePlaceholderForTheStartupGuard() throws IOException {
        StandardEnvironment env = prod(Map.of());

        assertThatThrownBy(() -> env.getProperty("frontend.url")).isInstanceOf(IllegalArgumentException.class);
        // MCP is optional; an empty backend URL is rejected by McpStartupGuard only when MCP is enabled
        assertThat(env.getProperty("app.mcp.backend-url")).isEmpty();
    }

    @Test
    void securityHeadersDefaultToEnforcedCspWithoutHsts() throws IOException {
        StandardEnvironment env = prod(Map.of("APP_URL", APP_URL));

        assertThat(env.getProperty("app.security.headers.csp-mode")).isEqualTo("enforce");
        assertThat(env.getProperty("app.security.headers.hsts-enabled")).isEqualTo("false");
    }

    @Test
    void securityHeadersFollowTheirVariables() throws IOException {
        StandardEnvironment env = prod(Map.of("APP_URL", APP_URL, "CSP_MODE", "report", "HSTS_ENABLED", "true"));

        assertThat(env.getProperty("app.security.headers.csp-mode")).isEqualTo("report");
        assertThat(env.getProperty("app.security.headers.hsts-enabled")).isEqualTo("true");
    }

    @Test
    void devKeepsTheViteDevServerAsFrontendUrlUnlessAppUrlIsSet() throws IOException {
        assertThat(env(Map.of(), "application.yml", "application-dev.yml").getProperty("frontend.url"))
                .isEqualTo("http://localhost:3000");
        assertThat(env(Map.of("APP_URL", APP_URL), "application.yml", "application-dev.yml")
                .getProperty("frontend.url")).isEqualTo(APP_URL);
    }

    /** gzip for the SPA's static types only; JSON stays uncompressed (BREACH). */
    @Test
    void compressionCoversStaticTypesOnly() throws IOException {
        StandardEnvironment env = prod(Map.of("APP_URL", APP_URL));

        assertThat(env.getProperty("server.compression.enabled")).isEqualTo("true");
        assertThat(env.getProperty("server.compression.min-response-size")).isEqualTo("1024");
        assertThat(env.getProperty("server.compression.mime-types"))
                .isEqualTo("text/html,text/css,text/javascript,application/javascript,image/svg+xml")
                .doesNotContain("json");
    }
}
