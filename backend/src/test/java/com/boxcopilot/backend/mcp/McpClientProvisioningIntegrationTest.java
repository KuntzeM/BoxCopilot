package com.boxcopilot.backend.mcp;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.mcp.authserver.McpAuthorizationServerEndpoints;
import com.boxcopilot.backend.mcp.security.McpOAuth2Attributes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The provisioning endpoints of the "Claude connection" dialog on the real application chain: session, CSRF, and
 * a user id that comes from the session and from nowhere else.
 */
class McpClientProvisioningIntegrationTest extends McpIntegrationTestBase {

    private static final String CLIENT_ENDPOINT = "/api/v1/me/mcp-client";
    private static final String REGENERATE_ENDPOINT = "/api/v1/me/mcp-client/regenerate";
    private static final String CONNECTIONS_ENDPOINT = "/api/v1/me/mcp-connections";
    private static final String CLAUDE_REDIRECT_URI = "https://claude.ai/api/mcp/auth_callback";
    private static final String CODE_CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

    @Autowired
    private ObjectMapper objectMapper;

    private ListAppender<ILoggingEvent> logAppender;
    private ch.qos.logback.classic.Logger mcpLogger;

    @BeforeEach
    void captureMcpLogs() {
        mcpLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("com.boxcopilot.backend");
        mcpLogger.setLevel(Level.TRACE);
        logAppender = new ListAppender<>();
        logAppender.start();
        mcpLogger.addAppender(logAppender);
    }

    @AfterEach
    void releaseMcpLogs() {
        mcpLogger.detachAppender(logAppender);
    }

    // ---------------------------------------------------------------- provisioning

    @Test
    void theClientIsCreatedLazilyAndTheSameIdIsReturnedForever() throws Exception {
        User user = createUser(true, true);
        MockHttpSession session = sessionFor(user);

        assertThat(clientRows()).isZero();
        String first = clientId(provision(session));
        String second = clientId(provision(session));

        assertThat(first).isEqualTo(second).isNotBlank();
        assertThat(clientRows()).isEqualTo(1);
        assertThat(registeredClientRepository.findByClientId(first).getClientSettings()
                .<String>getSetting(McpOAuth2Attributes.CLIENT_USER_ID)).isEqualTo(String.valueOf(user.getId()));
    }

    @Test
    void theProvisionedClientCarriesTheProductionSettings() throws Exception {
        User user = createUser(true, true);

        String clientId = clientId(provision(sessionFor(user)));

        var client = registeredClientRepository.findByClientId(clientId);
        assertThat(client.getScopes()).containsExactly(McpOAuth2Attributes.SCOPE_READ);
        assertThat(client.getRedirectUris()).containsExactlyInAnyOrderElementsOf(REDIRECT_URIS);
        assertThat(client.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(client.getClientSettings().isRequireAuthorizationConsent()).isTrue();
        assertThat(client.getTokenSettings().isReuseRefreshTokens()).isFalse();
    }

    /** The freshly provisioned client is usable: the authorization server accepts it and asks for consent. */
    @Test
    void theProvisionedClientCanStartAnAuthorization() throws Exception {
        User user = createUser(true, true);
        MockHttpSession session = sessionFor(user);

        String clientId = clientId(provision(session));

        assertThat(authorizeStatus(clientId, session)).isEqualTo(302);
    }

    // ---------------------------------------------------------------- regenerate

    @Test
    void regenerateReplacesTheClientAndEndsEveryConnection() throws Exception {
        User user = createUser(true, true);
        MockHttpSession session = sessionFor(user);
        String oldClientId = clientId(provision(session));
        saveAuthorization(registeredClientRepository.findByClientId(oldClientId), user.getId(), "old-access-token");
        assertThat(authorizationRows()).isEqualTo(1);

        String newClientId = clientId(perform(post(REGENERATE_ENDPOINT), session));

        assertThat(newClientId).isNotEqualTo(oldClientId);
        assertThat(registeredClientRepository.findByClientId(oldClientId)).isNull();
        assertThat(registeredClientRepository.findByClientId(newClientId)).isNotNull();
        assertThat(clientRows()).isEqualTo(1);
        assertThat(authorizationRows()).isZero();
        assertThat(mcpStatus("old-access-token")).isEqualTo(401);
        assertThat(authorizeStatus(oldClientId, session)).isNotEqualTo(302);
        assertThat(authorizeStatus(newClientId, session)).isEqualTo(302);
    }

    // ---------------------------------------------------------------- revoke connections

    @Test
    void revokingTheConnectionsKeepsTheClientAndKillsTheTokens() throws Exception {
        User user = createUser(true, true);
        MockHttpSession session = sessionFor(user);
        String clientId = clientId(provision(session));
        saveAuthorization(registeredClientRepository.findByClientId(clientId), user.getId(), "live-access-token");

        MvcResult result = perform(delete(CONNECTIONS_ENDPOINT), session);

        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(result.getResponse().getContentAsString()).isEmpty();
        assertThat(registeredClientRepository.findByClientId(clientId)).isNotNull();
        assertThat(authorizationRows()).isZero();
        assertThat(mcpStatus("live-access-token")).isEqualTo(401);
    }

    /** Deleting by principal covers exactly the connections of that user's client - and nobody else's. */
    @Test
    void revokingTheConnectionsLeavesEveryOtherUserAlone() throws Exception {
        User mine = createUser(true, true);
        User other = createUser(true, true);
        String myClientId = clientId(provision(sessionFor(mine)));
        String otherClientId = clientId(provision(sessionFor(other)));
        saveAuthorization(registeredClientRepository.findByClientId(myClientId), mine.getId(), "mine");
        saveAuthorization(registeredClientRepository.findByClientId(otherClientId), other.getId(), "theirs");

        perform(delete(CONNECTIONS_ENDPOINT), sessionFor(mine));

        assertThat(authorizationRows()).isEqualTo(1);
        assertThat(mcpStatus("theirs")).isNotEqualTo(401);
        assertThat(clientRows()).isEqualTo(2);
    }

    // ---------------------------------------------------------------- who is the caller

    @Test
    void theUserIdComesFromTheSessionAndNeverFromTheRequest() throws Exception {
        User mine = createUser(true, true);
        User other = createUser(true, true);
        String myClientId = clientId(provision(sessionFor(mine)));
        String otherClientId = clientId(provision(sessionFor(other)));

        MvcResult regenerated = perform(post(REGENERATE_ENDPOINT).param("userId", String.valueOf(other.getId())),
                sessionFor(mine));

        assertThat(clientId(regenerated)).isNotEqualTo(myClientId).isNotEqualTo(otherClientId);
        assertThat(registeredClientRepository.findByClientId(otherClientId)).isNotNull();
    }

    @Test
    void withoutASessionNothingIsProvisioned() throws Exception {
        MvcResult result = mockMvc.perform(post(CLIENT_ENDPOINT).with(csrf())).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(clientRows()).isZero();
    }

    @Test
    void everyWritingEndpointNeedsACsrfToken() throws Exception {
        User user = createUser(true, true);
        List<MockHttpServletRequestBuilder> requests = List.of(post(CLIENT_ENDPOINT), post(REGENERATE_ENDPOINT),
                delete(CONNECTIONS_ENDPOINT));

        for (MockHttpServletRequestBuilder request : requests) {
            MvcResult result = mockMvc.perform(request.session(sessionFor(user))).andReturn();

            assertThat(result.getResponse().getStatus()).isEqualTo(403);
        }
        assertThat(clientRows()).isZero();
    }

    // ---------------------------------------------------------------- the gate

    @Test
    void aUserWithoutMcpAccessIsRefusedEverywhere() throws Exception {
        User user = createUser(true, false);
        MockHttpSession session = sessionFor(user);

        assertThat(perform(post(CLIENT_ENDPOINT), session).getResponse().getStatus()).isEqualTo(403);
        assertThat(perform(post(REGENERATE_ENDPOINT), session).getResponse().getStatus()).isEqualTo(403);
        assertThat(perform(delete(CONNECTIONS_ENDPOINT), session).getResponse().getStatus()).isEqualTo(403);
        assertThat(clientRows()).isZero();
    }

    @Test
    void aDisabledUserIsRefused() throws Exception {
        User user = createUser(false, true);

        assertThat(perform(post(CLIENT_ENDPOINT), sessionFor(user)).getResponse().getStatus()).isEqualTo(403);
    }

    /** A temporary lock denies, but it deletes nothing: the client and its connections survive it. */
    @Test
    void aLockedUserIsRefusedWithoutLosingTheClient() throws Exception {
        User user = createUser(true, true);
        String clientId = clientId(provision(sessionFor(user)));
        saveAuthorization(registeredClientRepository.findByClientId(clientId), user.getId(), "locked-user-token");
        user.setLockedUntil(LocalDateTime.now().plusHours(1));
        userRepository.save(user);

        assertThat(perform(post(CLIENT_ENDPOINT), sessionFor(user)).getResponse().getStatus()).isEqualTo(403);
        assertThat(perform(delete(CONNECTIONS_ENDPOINT), sessionFor(user)).getResponse().getStatus()).isEqualTo(403);
        assertThat(registeredClientRepository.findByClientId(clientId)).isNotNull();
        assertThat(authorizationRows()).isEqualTo(1);
    }

    // ---------------------------------------------------------------- /api/v1/me

    @Test
    void meReportsMcpAllowedOnlyForAnAuthenticatedCallerThatIsAllowed() throws Exception {
        User allowed = createUser(true, true);
        User notAllowed = createUser(true, false);

        MvcResult anonymous = mockMvc.perform(get("/api/v1/me")).andReturn();
        MvcResult allowedResult = mockMvc.perform(get("/api/v1/me").session(sessionFor(allowed))).andReturn();
        MvcResult notAllowedResult = mockMvc.perform(get("/api/v1/me").session(sessionFor(notAllowed))).andReturn();

        assertThat(anonymous.getResponse().getContentAsString()).doesNotContain("mcpAllowed");
        assertThat(allowedResult.getResponse().getContentAsString()).contains("\"mcpAllowed\":true");
        assertThat(notAllowedResult.getResponse().getContentAsString()).contains("\"mcpAllowed\":false");
    }

    /** The flag follows the database, so an admin withdrawing it takes the dialog away on the next load. */
    @Test
    void meFollowsTheCurrentStateOfTheUser() throws Exception {
        User user = createUser(true, true);
        user.setMcpAllowed(false);
        userRepository.save(user);

        MvcResult result = mockMvc.perform(get("/api/v1/me").session(sessionFor(user))).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("\"mcpAllowed\":false");
    }

    // ---------------------------------------------------------------- logging

    @Test
    void noClientIdEverReachesTheLogs() throws Exception {
        User user = createUser(true, true);
        MockHttpSession session = sessionFor(user);

        String first = clientId(provision(session));
        String second = clientId(perform(post(REGENERATE_ENDPOINT), session));
        perform(delete(CONNECTIONS_ENDPOINT), session);

        assertThat(logAppender.list).isNotEmpty();
        for (ILoggingEvent event : logAppender.list) {
            assertThat(event.getFormattedMessage()).doesNotContain(first).doesNotContain(second);
        }
    }

    // ---------------------------------------------------------------- helpers

    private MvcResult provision(MockHttpSession session) throws Exception {
        return perform(post(CLIENT_ENDPOINT), session);
    }

    private MvcResult perform(MockHttpServletRequestBuilder request, MockHttpSession session) throws Exception {
        return mockMvc.perform(request.session(session).with(csrf())).andReturn();
    }

    private String clientId(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus())
                .as("response: %s", result.getResponse().getContentAsString()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.fieldNames()).toIterable().containsExactly("clientId");
        return body.get("clientId").asText();
    }

    /** Built as a query string, exactly as Claude would open it in the browser. */
    private int authorizeStatus(String clientId, MockHttpSession session) throws Exception {
        String uri = McpAuthorizationServerEndpoints.AUTHORIZE + "?response_type=code"
                + "&client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(CLAUDE_REDIRECT_URI)
                + "&scope=" + encode(McpOAuth2Attributes.SCOPE_READ)
                + "&state=state-" + Math.abs(clientId.hashCode())
                + "&code_challenge=" + encode(CODE_CHALLENGE)
                + "&code_challenge_method=S256";
        // A ready-made URI, not a template: MockMvc would encode the percent signs of the query again.
        return mockMvc.perform(get(URI.create(uri)).session(session)).andReturn().getResponse().getStatus();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private int mcpStatus(String accessToken) throws Exception {
        return mockMvc.perform(post(McpEndpoints.MCP)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(HttpHeaders.ACCEPT, ACCEPT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(toolsListRequest()))
                .andReturn().getResponse().getStatus();
    }

    private int clientRows() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM oauth2_registered_client", Integer.class);
    }

    private int authorizationRows() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM oauth2_authorization", Integer.class);
    }
}
