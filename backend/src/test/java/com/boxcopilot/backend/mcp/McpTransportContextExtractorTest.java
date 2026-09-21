package com.boxcopilot.backend.mcp;

import io.modelcontextprotocol.common.McpTransportContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.ServerRequest;

import java.security.Principal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class McpTransportContextExtractorTest {

    private final McpTransportContextExtractor extractor = new McpTransportContextExtractor();

    private static ServerRequest requestWith(Principal principal) {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/mcp");
        servletRequest.setUserPrincipal(principal);
        return ServerRequest.create(servletRequest, List.of());
    }

    @Test
    void putsNumericPrincipalNameIntoContextAsLong() {
        McpTransportContext context = extractor.extract(requestWith(() -> "42"));

        assertThat(context.get(McpTransportContextKeys.USER_ID)).isEqualTo(42L);
    }

    @Test
    void contextHasNoUserIdWithoutPrincipal() {
        McpTransportContext context = extractor.extract(requestWith(null));

        assertThat(context.get(McpTransportContextKeys.USER_ID)).isNull();
    }

    @Test
    void contextHasNoUserIdForBlankOrNonNumericOrNonPositiveNames() {
        for (String name : new String[] {"", "  ", "abc", "4x", "0", "-5", "99999999999999999999", null}) {
            McpTransportContext context = extractor.extract(requestWith(() -> name));

            assertThat(context.get(McpTransportContextKeys.USER_ID)).as("name '%s'", name).isNull();
        }
    }
}
