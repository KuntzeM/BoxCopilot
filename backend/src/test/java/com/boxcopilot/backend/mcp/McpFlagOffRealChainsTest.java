package com.boxcopilot.backend.mcp;

import com.boxcopilot.backend.domain.AuthProvider;
import com.boxcopilot.backend.domain.Role;
import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.mcp.authserver.McpSessionUserResolver;
import com.boxcopilot.backend.repository.UserRepository;
import com.boxcopilot.backend.mcp.security.McpAccessPolicy;
import com.boxcopilot.backend.mcp.web.McpPendingCookies;
import com.boxcopilot.backend.service.McpAllowanceProbe;
import com.boxcopilot.backend.service.McpPendingProbe;
import jakarta.servlet.http.Cookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.web.SecurityFilterChain;
import com.boxcopilot.backend.mcp.security.McpAuthorizationService;
import com.boxcopilot.backend.mcp.security.McpOpaqueTokenIntrospector;
import com.nimbusds.jose.jwk.source.JWKSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.boxcopilot.backend.repository.PostgresContainerSupport;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The same real chains the MCP tests use, but with the flag off: no MCP bean, no authorization-server bean, and
 * neither {@code /mcp} nor the metadata endpoint answers anything - anonymous or with a valid session.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "app.mcp.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "mcp-test"})
@ContextConfiguration(initializers = PostgresContainerSupport.class)
@Import(McpIntegrationTestBase.NoRemoteIdentityProvider.class)
class McpFlagOffRealChainsTest {

    private static final String[] MCP_PATHS = {"/mcp", "/.well-known/oauth-protected-resource/mcp",
            "/.well-known/oauth-authorization-server", "/oauth2/authorize", "/oauth2/token", "/oauth2/consent",
            "/oauth2/revoke", "/oauth2/jwks", "/api/v1/mcp/continue"};

    @Autowired
    private ApplicationContext context;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;

    private static MockHttpSession authenticatedSession() {
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("someone", null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT", securityContext);
        return session;
    }

    @Test
    void neitherMcpNorAuthorizationServerContributesABean() {
        assertThat(context.getBeansOfType(McpProperties.class)).isEmpty();
        assertThat(context.getBeansOfType(BoxPilotMcpTools.class)).isEmpty();
        assertThat(context.getBeansOfType(McpAccessPolicy.class)).isEmpty();
        assertThat(context.getBeansOfType(McpAuthorizationService.class)).isEmpty();
        assertThat(context.getBeansOfType(McpOpaqueTokenIntrospector.class)).isEmpty();
        assertThat(context.getBeansOfType(OAuth2AuthorizationService.class)).isEmpty();
        assertThat(context.getBeansOfType(OpaqueTokenIntrospector.class)).isEmpty();
        assertThat(context.getBeansOfType(RegisteredClientRepository.class)).isEmpty();
        assertThat(context.getBeansOfType(AuthorizationServerSettings.class)).isEmpty();
        assertThat(context.getBeansOfType(JWKSource.class)).isEmpty();
        assertThat(context.containsBean("webMvcStatelessServerRouterFunction")).isFalse();
        assertThat(context.getBeansOfType(OAuth2AuthorizationConsentService.class)).isEmpty();
        assertThat(context.getBeansOfType(McpSessionUserResolver.class)).isEmpty();
        assertThat(context.getBeansOfType(McpPendingCookies.class)).isEmpty();
        // Only the application chain is left
        assertThat(context.getBeansOfType(SecurityFilterChain.class)).hasSize(1);
    }

    /** The neutral port falls back to "nothing pending", even when a leftover cookie is sent. */
    @Test
    void thePendingProbeFallsBackToNothingPending() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("mcp_authz", "c3RhdGU9MQ"));

        assertThat(context.getBean(McpPendingProbe.class).isPending(request)).isFalse();
    }

    /** The neutral port falls back to "not allowed", so the SPA never offers the dialog. */
    @Test
    void theAllowanceProbeFallsBackToNotAllowed() {
        assertThat(context.getBean(McpAllowanceProbe.class).isAllowed(1L)).isFalse();
    }

    /** Even a user the database allows is not allowed while the feature is off. */
    @Test
    void meReportsThatMcpIsNotAllowed() throws Exception {
        User user = new User("mcp-test-user-" + UUID.randomUUID(), "MCP user", AuthProvider.LOCAL, Role.USER);
        user.setEnabled(true);
        user.setMcpAllowed(true);
        User saved = userRepository.save(user);
        try {
            MvcResult result = mockMvc.perform(get("/api/v1/me")
                    .session(McpIntegrationTestBase.sessionFor(saved))).andReturn();

            assertThat(result.getResponse().getContentAsString()).contains("\"mcpAllowed\":false");
        } finally {
            userRepository.delete(saved);
        }
    }

    /** Without the feature there is no provisioning endpoint at all, not even for an authenticated caller. */
    @Test
    void theProvisioningEndpointsAreGone() throws Exception {
        MockHttpSession session = authenticatedSession();

        assertThat(mockMvc.perform(post("/api/v1/me/mcp-client").session(session).with(csrf()))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(mockMvc.perform(post("/api/v1/me/mcp-client/regenerate").session(session).with(csrf()))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(mockMvc.perform(delete("/api/v1/me/mcp-connections").session(session).with(csrf()))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void noMcpPathAnswersAnonymously() throws Exception {
        for (String path : MCP_PATHS) {
            MvcResult result = mockMvc.perform(get(path)).andReturn();

            assertThat(result.getResponse().getStatus() / 100).as(path).isNotEqualTo(2);
            assertThat(result.getResponse().getContentAsString()).as(path)
                    .doesNotContain("scopes_supported", "authorization_endpoint", "serverInfo");
        }
    }

    @Test
    void noMcpPathAnswersWithASession() throws Exception {
        MockHttpSession session = authenticatedSession();
        for (String path : MCP_PATHS) {
            MvcResult result = mockMvc.perform(get(path).session(session)).andReturn();

            assertThat(result.getResponse().getStatus() / 100).as(path).isNotEqualTo(2);
            assertThat(result.getResponse().getContentAsString()).as(path)
                    .doesNotContain("scopes_supported", "authorization_endpoint", "serverInfo");
        }
    }
}
