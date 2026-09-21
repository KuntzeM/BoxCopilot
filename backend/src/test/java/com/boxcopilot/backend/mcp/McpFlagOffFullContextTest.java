package com.boxcopilot.backend.mcp;

import io.modelcontextprotocol.server.McpStatelessSyncServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
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
