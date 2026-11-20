package com.boxcopilot.backend.mcp;

import com.boxcopilot.backend.mcp.authserver.McpAuthorizationServerEndpoints;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * HSTS is governed by HSTS_ENABLED alone (default off): neither MCP chain may add Spring Security's own HSTS
 * header on a secure request. The browser headers of SecurityHeadersFilter still reach these responses.
 */
class McpHstsIntegrationTest extends McpIntegrationTestBase {

    private void assertNoHsts(MockHttpServletResponse response) {
        assertThat(response.getHeader("Strict-Transport-Security")).isNull();
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    void theAuthorizationServerChainSendsNoHstsWhenDisabled() throws Exception {
        assertNoHsts(mockMvc.perform(get(McpAuthorizationServerEndpoints.METADATA).secure(true))
                .andReturn().getResponse());
    }

    @Test
    void theResourceServerChainSendsNoHstsWhenDisabled() throws Exception {
        assertNoHsts(mockMvc.perform(get(McpEndpoints.PROTECTED_RESOURCE_METADATA).secure(true))
                .andReturn().getResponse());
        assertNoHsts(mockMvc.perform(post(McpEndpoints.MCP).secure(true)).andReturn().getResponse());
    }
}
