package com.boxcopilot.backend.mcp;

import com.boxcopilot.backend.mcp.abuse.McpRateLimits;
import com.boxcopilot.backend.mcp.observability.McpAuditLogger;
import com.boxcopilot.backend.mcp.observability.McpToolMetrics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Runs the real Spring AI MCP auto-configuration (STATELESS, WebMvc) with the values of the real
 * {@code application.yml}; only security, the read service and the user gate are stubbed.
 */
@SpringBootTest(classes = McpProtocolTest.TestApplication.class, properties = {
        "app.mcp.enabled=true",
        "app.mcp.backend-url=https://boxpilot.example.com"})
@AutoConfigureMockMvc
@ActiveProfiles("mcp-test")
class McpProtocolTest {

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    @ImportAutoConfiguration({
            WebMvcAutoConfiguration.class,
            DispatcherServletAutoConfiguration.class,
            HttpMessageConvertersAutoConfiguration.class,
            JacksonAutoConfiguration.class,
            SecurityAutoConfiguration.class,
            org.springframework.ai.mcp.server.common.autoconfigure.McpServerObjectMapperAutoConfiguration.class,
            org.springframework.ai.mcp.server.common.autoconfigure.McpServerAutoConfiguration.class,
            org.springframework.ai.mcp.server.common.autoconfigure.McpServerStatelessAutoConfiguration.class,
            org.springframework.ai.mcp.server.common.autoconfigure.ToolCallbackConverterAutoConfiguration.class,
            org.springframework.ai.mcp.server.common.autoconfigure.StatelessToolCallbackConverterAutoConfiguration.class,
            org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerAnnotationScannerAutoConfiguration.class,
            org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerSpecificationFactoryAutoConfiguration.class,
            org.springframework.ai.mcp.server.common.autoconfigure.annotations.StatelessServerSpecificationFactoryAutoConfiguration.class,
            org.springframework.ai.mcp.server.autoconfigure.McpServerStatelessWebMvcAutoConfiguration.class,
            org.springframework.ai.mcp.server.autoconfigure.McpServerStreamableHttpWebMvcAutoConfiguration.class,
            org.springframework.ai.mcp.server.autoconfigure.McpServerSseWebMvcAutoConfiguration.class})
    @Import({McpConfiguration.class, McpTransportConfiguration.class, McpStartupGuard.class,
            McpRateLimits.class, McpAuditLogger.class, McpToolMetrics.class, BoxPilotMcpTools.class})
    static class TestApplication {

        @Bean
        SecurityFilterChain testChain(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                    .build();
        }
    }

    private static final String ACCEPT = "application/json, text/event-stream";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private McpReadService readService;
    @MockitoBean
    private McpUserGate userGate;

    /** The transport only trusts a bearer token, so the tests authenticate the way the resource server does. */
    private static BearerTokenAuthentication bearerToken(String subject) {
        java.time.Instant now = java.time.Instant.now();
        var principal = new DefaultOAuth2AuthenticatedPrincipal(subject, java.util.Map.of("sub", subject),
                List.of(new SimpleGrantedAuthority("SCOPE_mcp:read")));
        var accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "token-value", now,
                now.plusSeconds(1800));
        return new BearerTokenAuthentication(principal, accessToken, principal.getAuthorities());
    }

    private MvcResult rpc(String userName, String body) throws Exception {
        return rpc(userName == null ? null : bearerToken(userName), body);
    }

    private MvcResult rpc(org.springframework.security.core.Authentication authentication, String body)
            throws Exception {
        var request = post("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Accept", ACCEPT)
                .content(body);
        if (authentication != null) {
            request = request.with(authentication(authentication));
        }
        return mockMvc.perform(request).andReturn();
    }

    private JsonNode rpcJson(String userName, String body) throws Exception {
        MvcResult result = rpc(userName, body);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String call(String tool, String arguments) {
        return """
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"%s","arguments":%s}}"""
                .formatted(tool, arguments);
    }

    @Test
    void initializeAnswersWithServerInfoAndToolCapability() throws Exception {
        JsonNode response = rpcJson("42", """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18",
                "capabilities":{},"clientInfo":{"name":"test","version":"1"}}}""");

        assertThat(response.at("/result/serverInfo/name").asText()).isEqualTo("boxpilot");
        assertThat(response.at("/result/capabilities/tools").isMissingNode()).isFalse();
    }

    @Test
    void toolsListContainsTheFourReadOnlyTools() throws Exception {
        JsonNode response = rpcJson("42", """
                {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}""");

        JsonNode tools = response.at("/result/tools");
        assertThat(tools).hasSize(4);
        List<String> names = tools.findValuesAsText("name");
        assertThat(names).contains("search_items", "get_item_image", "search_boxes", "get_box_contents");
        for (JsonNode tool : tools) {
            assertThat(tool.at("/annotations/readOnlyHint").asBoolean()).as(tool.get("name").asText()).isTrue();
            assertThat(tool.at("/annotations/destructiveHint").asBoolean()).isFalse();
            assertThat(tool.get("description").asText()).containsIgnoringCase("untrusted");
        }
    }

    @Test
    void toolCallReceivesTheAuthenticatedUserThroughTheTransportContext() throws Exception {
        when(readService.searchItems(anyString(), anyInt())).thenReturn(
                List.of(new McpItemHit(7L, "Desk lamp", 12, List.of("Office"), false)));

        JsonNode response = rpcJson("42", call("search_items", "{\"query\":\"lamp\",\"limit\":5}"));

        assertThat(response.at("/result/isError").asBoolean()).isFalse();
        JsonNode body = objectMapper.readTree(response.at("/result/content/0/text").asText());
        assertThat(body.at("/items/0/name").asText()).isEqualTo("Desk lamp");
        verify(userGate).requireActiveUser(42L);
        verify(readService).searchItems("lamp", 5);
    }

    @Test
    void toolCallWithASessionAuthenticationIsDeniedAndReachesNoData() throws Exception {
        var sessionAuthentication = org.springframework.security.authentication.UsernamePasswordAuthenticationToken
                .authenticated("42", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        MvcResult result = rpc(sessionAuthentication, call("search_items", "{\"query\":\"lamp\"}"));
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());

        assertThat(response.at("/result/isError").asBoolean()).isTrue();
        assertThat(response.at("/result/content/0/text").asText()).isEqualTo(BoxPilotMcpTools.GENERIC_ERROR);
        verifyNoInteractions(userGate, readService);
    }

    @Test
    void toolCallForUserWithoutNumericIdIsDeniedAndReachesNoData() throws Exception {
        JsonNode response = rpcJson("not-a-number", call("search_items", "{\"query\":\"lamp\"}"));

        assertThat(response.at("/result/isError").asBoolean()).isTrue();
        assertThat(response.at("/result/content/0/text").asText()).isEqualTo(BoxPilotMcpTools.GENERIC_ERROR);
        verifyNoInteractions(userGate, readService);
    }

    @Test
    void toolCallDeniedByTheGateReturnsGenericErrorWithoutData() throws Exception {
        doThrow(new McpAccessDeniedException("user 42 is locked")).when(userGate).requireActiveUser(42L);

        JsonNode response = rpcJson("42", call("search_items", "{\"query\":\"lamp\"}"));

        assertThat(response.at("/result/isError").asBoolean()).isTrue();
        assertThat(response.at("/result/content/0/text").asText()).isEqualTo(BoxPilotMcpTools.GENERIC_ERROR);
        verifyNoInteractions(readService);
    }

    @Test
    void itemImageIsDeliveredAsBase64JpegImageContent() throws Exception {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, 9, 9};
        when(readService.itemImage(9L, ImageSize.THUMBNAIL))
                .thenReturn(Optional.of(new McpItemImage(jpeg, "Desk lamp", 12)));

        JsonNode response = rpcJson("42", call("get_item_image", "{\"itemId\":9}"));

        assertThat(response.at("/result/isError").asBoolean()).isFalse();
        assertThat(response.at("/result/content/0/type").asText()).isEqualTo("text");
        assertThat(response.at("/result/content/1/type").asText()).isEqualTo("image");
        assertThat(response.at("/result/content/1/mimeType").asText()).isEqualTo("image/jpeg");
        assertThat(java.util.Base64.getDecoder().decode(response.at("/result/content/1/data").asText()))
                .isEqualTo(jpeg);
    }

    @Test
    void unauthenticatedRequestNeverReachesTheTools() throws Exception {
        MvcResult result = rpc((String) null, call("search_items", "{\"query\":\"lamp\"}"));

        assertThat(result.getResponse().getStatus()).isIn(401, 403);
        verifyNoInteractions(userGate, readService);
    }

    @Test
    void statelessToolCallsRunInTheRequestThreadWithTheSecurityContextPopulated() throws Exception {
        AtomicReference<Thread> toolThread = new AtomicReference<>();
        AtomicReference<Object> toolAuthentication = new AtomicReference<>();
        when(readService.searchItems(anyString(), anyInt())).thenAnswer(invocation -> {
            toolThread.set(Thread.currentThread());
            toolAuthentication.set(SecurityContextHolder.getContext().getAuthentication());
            return List.of();
        });

        rpcJson("42", call("search_items", "{\"query\":\"lamp\"}"));

        assertThat(toolThread.get()).isSameAs(Thread.currentThread());
        assertThat(toolAuthentication.get()).isNotNull();
        verify(userGate).requireActiveUser(42L);
    }
}
