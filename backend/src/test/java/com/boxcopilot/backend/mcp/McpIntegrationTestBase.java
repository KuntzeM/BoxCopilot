package com.boxcopilot.backend.mcp;

import com.boxcopilot.backend.domain.AuthProvider;
import com.boxcopilot.backend.domain.Role;
import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.mcp.security.McpAuthorizationService;
import com.boxcopilot.backend.mcp.security.McpOAuth2Attributes;
import com.boxcopilot.backend.repository.PostgresContainerSupport;
import com.boxcopilot.backend.repository.UserRepository;
import com.boxcopilot.backend.service.CustomUserDetailsService;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Base for MCP tests that need the real thing: a Postgres container with the production Flyway schema, the
 * production security chains (profile {@code dev} supplies the application chain, {@code mcp-test} replaces the
 * machine-specific values) and MCP switched on. The forbidden {@code test} profile and {@code NoSecurityConfig}
 * are deliberately absent.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "app.mcp.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "mcp-test"})
@ContextConfiguration(initializers = PostgresContainerSupport.class)
@Import(McpIntegrationTestBase.NoRemoteIdentityProvider.class)
public abstract class McpIntegrationTestBase {

    protected static final String ACCEPT = "application/json, text/event-stream";

    /** The redirect URIs of {@code application-mcp-test.yml}; the first one is Claude's callback. */
    protected static final List<String> REDIRECT_URIS =
            List.of("https://claude.ai/api/mcp/auth_callback", "http://127.0.0.1:41000/callback");

    private static final String TEST_USER_PREFIX = "mcp-test-user-";

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected RegisteredClientRepository registeredClientRepository;
    @Autowired
    protected McpAuthorizationService authorizationService;
    @Autowired
    protected JdbcTemplate jdbcTemplate;
    @Autowired
    protected ApplicationContext applicationContext;

    /**
     * Replaces the OIDC client registration of the {@code dev} profile. Its {@code issuer-uri} would be resolved
     * over the network while the context starts; this hand-built registration backs that auto-configuration off.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class NoRemoteIdentityProvider {

        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("nextcloud")
                    .clientId("test-client")
                    .clientSecret("test-secret")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .redirectUri("http://localhost:8080/login/oauth2/code/{registrationId}")
                    .scope("openid")
                    .authorizationUri("http://localhost/authorize")
                    .tokenUri("http://localhost/token")
                    .jwkSetUri("http://localhost/jwks")
                    .issuerUri("http://localhost")
                    .userNameAttributeName(IdTokenClaimNames.SUB)
                    .build());
        }
    }

    /**
     * The authorization server chain, the MCP chain and the application chain, and nothing else. A fourth one
     * would mean Boot's resource-server or authorization-server defaults came back and started deciding about
     * requests.
     */
    @Test
    void exactlyThreeSecurityFilterChainsAreRegistered() {
        assertThat(applicationContext.getBeansOfType(SecurityFilterChain.class)).hasSize(3);
    }

    /**
     * No persistence context spans a request. Every MCP path reads the user twice - the resource server through
     * the introspector and then the tools, the authorization server through the session resolver and then the
     * gate, the provisioning endpoints through the controller and then the gate - and the second read is the
     * one that decides. With an open session in view it would be answered from the first-level cache of the
     * first read, and a withdrawal that commits in between would go unnoticed.
     */
    @Test
    void noPersistenceContextSpansTheRequest() {
        assertThat(applicationContext.getBeansOfType(OpenEntityManagerInViewInterceptor.class)).isEmpty();
    }

    /** The users are committed rows, so they are removed by their generated name rather than rolled back. */
    @BeforeEach
    void clearOAuth2TablesAndTestUsers() {
        jdbcTemplate.update("DELETE FROM oauth2_authorization");
        jdbcTemplate.update("DELETE FROM oauth2_registered_client");
        jdbcTemplate.update("DELETE FROM users WHERE username LIKE ?", TEST_USER_PREFIX + "%");
    }

    protected User createUser(boolean enabled, boolean mcpAllowed) {
        User user = new User(TEST_USER_PREFIX + UUID.randomUUID(), "MCP user", AuthProvider.LOCAL, Role.USER);
        user.setEnabled(enabled);
        user.setMcpAllowed(mcpAllowed);
        return userRepository.save(user);
    }

    /** A public client as the connection provisioning will create it: bound to exactly one user. */
    protected RegisteredClient createClient(long boundUserId) {
        RegisteredClient client = RegisteredClientFixtures.publicClient(boundUserId, REDIRECT_URIS);
        registeredClientRepository.save(client);
        return client;
    }

    /** A session holding the application's own principal, exactly as a form login leaves it behind. */
    protected static MockHttpSession sessionFor(User user) {
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new CustomUserDetailsService.CustomUserPrincipal(user), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        return session;
    }

    protected static SecurityContext storedSecurityContext(MockHttpSession session) {
        return (SecurityContext) session
                .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
    }

    protected OAuth2Authorization.Builder authorizationBuilder(RegisteredClient client, long userId,
                                                               String accessTokenValue, Instant accessTokenExpiry) {
        Instant issuedAt = accessTokenExpiry.minus(Duration.ofMinutes(30));
        return OAuth2Authorization.withRegisteredClient(client)
                .id(UUID.randomUUID().toString())
                .principalName(String.valueOf(userId))
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizedScopes(Set.of(McpOAuth2Attributes.SCOPE_READ))
                .token(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, accessTokenValue, issuedAt,
                        accessTokenExpiry, Set.of(McpOAuth2Attributes.SCOPE_READ)))
                .refreshToken(new OAuth2RefreshToken("refresh-" + accessTokenValue, issuedAt,
                        issuedAt.plus(Duration.ofDays(7))));
    }

    /** Stores a connection that is valid right now; the decorator stamps its start on this first save. */
    protected OAuth2Authorization saveAuthorization(RegisteredClient client, long userId, String accessTokenValue) {
        OAuth2Authorization authorization = authorizationBuilder(client, userId, accessTokenValue,
                Instant.now().plus(Duration.ofMinutes(30))).build();
        authorizationService.save(authorization);
        return authorization;
    }

    protected static String toolsListRequest() {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}";
    }
}
