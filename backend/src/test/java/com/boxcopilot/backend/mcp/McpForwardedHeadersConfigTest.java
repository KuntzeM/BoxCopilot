package com.boxcopilot.backend.mcp;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.catalina.filters.RemoteIpFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M4: the production configuration must not trust every caller's {@code X-Forwarded-For}. Only a request that
 * really arrives from the internal proxy network may name the client address, because the IP rate limits and the
 * audit log would otherwise count and record whatever a caller claims.
 *
 * <p>The behaviour is proven against Tomcat's own implementation of the algorithm: {@link RemoteIpFilter} shares
 * the {@code internalProxies} pattern and the resolution logic with the {@code RemoteIpValve} that
 * {@code forward-headers-strategy: native} installs, but unlike the valve it runs on a plain servlet request.
 */
class McpForwardedHeadersConfigTest {

    private static final String STRATEGY = "server.forward-headers-strategy";
    private static final String INTERNAL_PROXIES = "server.tomcat.remoteip.internal-proxies";
    private static final String CLIENT = "203.0.113.7";

    private final StandardEnvironment production = productionEnvironment();

    private static StandardEnvironment productionEnvironment() {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources;
        try {
            sources = loader.load("application-prod", new ClassPathResource("application-prod.yml"));
        } catch (IOException e) {
            throw new IllegalStateException("application-prod.yml cannot be read", e);
        }
        StandardEnvironment environment = new StandardEnvironment();
        sources.forEach(environment.getPropertySources()::addFirst);
        return environment;
    }

    /** Resolves the configured pattern including its placeholder default, so the default is exercised too. */
    private String internalProxies() {
        return production.getProperty(INTERNAL_PROXIES);
    }

    private HttpServletRequest resolve(String remoteAddress, String forwardedFor) throws Exception {
        RemoteIpFilter filter = new RemoteIpFilter();
        filter.setInternalProxies(internalProxies());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", McpEndpoints.MCP);
        request.setRemoteAddr(remoteAddress);
        request.addHeader("X-Forwarded-For", forwardedFor);
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return (HttpServletRequest) chain.getRequest();
    }

    @Test
    void productionResolvesForwardedHeadersNativelyInsteadOfTrustingEveryCaller() {
        assertThat(production.getProperty(STRATEGY)).isEqualTo("native");
    }

    /** The override is a regular expression, and its name says so: CIDR notation would match nothing. */
    @Test
    void theTrustedProxyRangeIsConfigurableAndDefaultsToThePrivateAddressSpace() {
        assertThat(production.getProperty(INTERNAL_PROXIES, String.class))
                .isNotBlank();

        StandardEnvironment narrowed = productionEnvironment();
        narrowed.getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
                "operator", Map.of("TRUSTED_PROXY_PATTERN", "172\\.18\\.0\\.2")));

        assertThat(narrowed.getProperty(INTERNAL_PROXIES)).isEqualTo("172\\.18\\.0\\.2");
    }

    @Test
    void aForwardedHeaderFromTheInternalProxyNetworkNamesTheClient() throws Exception {
        for (String proxy : List.of("172.18.0.5", "10.0.0.9", "192.168.1.4", "127.0.0.1")) {
            assertThat(resolve(proxy, CLIENT).getRemoteAddr()).as("proxy %s", proxy).isEqualTo(CLIENT);
        }
    }

    @Test
    void aForwardedHeaderFromOutsideTheInternalProxyNetworkIsIgnored() throws Exception {
        for (String caller : List.of("198.51.100.23", "8.8.8.8", "172.32.0.1")) {
            assertThat(resolve(caller, CLIENT).getRemoteAddr()).as("caller %s", caller).isEqualTo(caller);
        }
    }

    /** Debug logging of the security packages would put tokens and principals into the production log. */
    @Test
    void productionLogsNothingAtDebugLevel() {
        production.getPropertySources().stream()
                .filter(source -> source instanceof org.springframework.core.env.EnumerablePropertySource<?>)
                .map(source -> (org.springframework.core.env.EnumerablePropertySource<?>) source)
                .filter(source -> source.getName().startsWith("application-prod"))
                .flatMap(source -> List.of(source.getPropertyNames()).stream())
                .filter(name -> name.startsWith("logging.level."))
                .forEach(name -> assertThat(String.valueOf(production.getProperty(name)).toUpperCase())
                        .as(name).isIn("INFO", "WARN", "ERROR", "OFF"));

        assertThat(production.getProperty("logging.level.org.springframework.security")).isEqualTo("INFO");
        assertThat(production.getProperty("logging.level.com.boxcopilot.backend.mcp")).isEqualTo("INFO");
    }
}
