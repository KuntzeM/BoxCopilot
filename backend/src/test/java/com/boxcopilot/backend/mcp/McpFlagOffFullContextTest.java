package com.boxcopilot.backend.mcp;

import com.boxcopilot.backend.mcp.security.McpAuthorizationService;
import com.boxcopilot.backend.mcp.security.McpOpaqueTokenIntrospector;
import com.nimbusds.jose.jwk.source.JWKSource;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** The complete application with the flag left at its default: no MCP beans and nothing answers on /mcp. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class McpFlagOffFullContextTest {

    @Autowired
    private ApplicationContext context;
    @Autowired
    private MockMvc mockMvc;

    @Test
    void noMcpBeansExist() {
        assertThat(context.getBeansOfType(McpStatelessSyncServer.class)).isEmpty();
        assertThat(context.getBeansOfType(BoxPilotMcpTools.class)).isEmpty();
        assertThat(context.getBeansOfType(McpReadService.class)).isEmpty();
        assertThat(context.getBeansOfType(McpProperties.class)).isEmpty();
        assertThat(context.containsBean("webMvcStatelessServerRouterFunction")).isFalse();
    }

    @Test
    void noAuthorizationServerOrResourceServerBeanExists() {
        assertThat(context.getBeansOfType(McpAuthorizationService.class)).isEmpty();
        assertThat(context.getBeansOfType(McpOpaqueTokenIntrospector.class)).isEmpty();
        assertThat(context.getBeansOfType(OAuth2AuthorizationService.class)).isEmpty();
        assertThat(context.getBeansOfType(RegisteredClientRepository.class)).isEmpty();
        assertThat(context.getBeansOfType(AuthorizationServerSettings.class)).isEmpty();
        assertThat(context.getBeansOfType(OpaqueTokenIntrospector.class)).isEmpty();
        assertThat(context.getBeansOfType(JWKSource.class)).isEmpty();
    }

    @Test
    void authorizationServerEndpointsAndProtectedResourceMetadataGiveNoSuccess() throws Exception {
        for (String path : new String[] {"/oauth2/authorize", "/oauth2/token", "/oauth2/jwks",
                "/.well-known/oauth-authorization-server", "/.well-known/oauth-protected-resource/mcp"}) {
            MvcResult result = mockMvc.perform(get(path)).andReturn();

            assertThat(result.getResponse().getStatus() / 100).as(path).isNotEqualTo(2);
            assertThat(result.getResponse().getContentAsString()).as(path)
                    .doesNotContain("authorization_endpoint", "resource", "scopes_supported");
        }
    }

    @Test
    void mcpEndpointGivesNoSuccessAndNoData() throws Exception {
        MvcResult post = mockMvc.perform(post("/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Accept", "application/json, text/event-stream")
                        .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}"))
                .andReturn();
        MvcResult get = mockMvc.perform(get("/mcp")).andReturn();

        assertThat(post.getResponse().getStatus() / 100).isNotEqualTo(2);
        assertThat(get.getResponse().getStatus() / 100).isNotEqualTo(2);
        assertThat(post.getResponse().getContentAsString()).doesNotContain("search_items", "serverInfo");
    }
}
