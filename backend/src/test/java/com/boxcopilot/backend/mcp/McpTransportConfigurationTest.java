package com.boxcopilot.backend.mcp;

import io.modelcontextprotocol.server.transport.DefaultServerTransportSecurityValidator;
import io.modelcontextprotocol.server.transport.ServerTransportSecurityException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The DNS-rebinding check derived from {@code app.mcp.backend-url}. */
class McpTransportConfigurationTest {

    private static Map<String, List<String>> headers(String host, String origin) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        if (host != null) {
            headers.put("host", List.of(host));
        }
        if (origin != null) {
            headers.put("origin", List.of(origin));
        }
        return headers;
    }

    /** Through the deprecated map entry point, because that is the one the Spring AI WebMvc transport calls. */
    @SuppressWarnings("deprecation")
    private static int status(DefaultServerTransportSecurityValidator validator, String host, String origin) {
        try {
            validator.validateHeaders(headers(host, origin));
            return 200;
        } catch (ServerTransportSecurityException e) {
            return e.getStatusCode();
        }
    }

    @Test
    void productionUrlAllowsItsHostAndOriginOnly() {
        var validator = McpTransportConfiguration.hostAndOriginOf("https://Box-Backend.example.com");

        assertThat(status(validator, "box-backend.example.com", null)).isEqualTo(200);
        assertThat(status(validator, "box-backend.example.com", "https://box-backend.example.com")).isEqualTo(200);
        assertThat(status(validator, "attacker.example", null)).isEqualTo(421);
        assertThat(status(validator, "box-backend.example.com.attacker.example", null)).isEqualTo(421);
        assertThat(status(validator, null, null)).isEqualTo(421);
        assertThat(status(validator, "box-backend.example.com", "http://box-backend.example.com")).isEqualTo(403);
        assertThat(status(validator, "box-backend.example.com", "https://attacker.example")).isEqualTo(403);
    }

    @Test
    void anExplicitPortIsPartOfTheAllowedHost() {
        var validator = McpTransportConfiguration.hostAndOriginOf("http://localhost:8080/");

        assertThat(status(validator, "localhost:8080", "http://localhost:8080")).isEqualTo(200);
        assertThat(status(validator, "localhost", null)).isEqualTo(421);
        assertThat(status(validator, "localhost:9090", null)).isEqualTo(421);
    }

    @Test
    void aMissingOrRelativeBackendUrlFailsInsteadOfAllowingEverything() {
        assertThatThrownBy(() -> McpTransportConfiguration.hostAndOriginOf(null))
                .hasMessageContaining("BACKEND_URL");
        assertThatThrownBy(() -> McpTransportConfiguration.hostAndOriginOf(" "))
                .hasMessageContaining("BACKEND_URL");
        assertThatThrownBy(() -> McpTransportConfiguration.hostAndOriginOf("/relative"))
                .hasMessageContaining("BACKEND_URL");
        assertThatCode(() -> McpTransportConfiguration.hostAndOriginOf("http://localhost"))
                .doesNotThrowAnyException();
    }
}
