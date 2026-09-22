package com.boxcopilot.backend.mcp;

import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.mcp.security.McpOAuth2Attributes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** The resource server chain end to end: real chains, real MCP protocol, real database. */
class McpResourceServerIntegrationTest extends McpIntegrationTestBase {

    private static final String EXPECTED_CHALLENGE =
            "Bearer resource_metadata=\"http://localhost/.well-known/oauth-protected-resource/mcp\", "
                    + "scope=\"mcp:read\"";

    @Autowired
    private ObjectMapper objectMapper;

    private MvcResult mcp(String bearerToken, String body) throws Exception {
        var request = post("/mcp").contentType(MediaType.APPLICATION_JSON).header("Accept", ACCEPT).content(body);
        if (bearerToken != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
        }
        return mockMvc.perform(request).andReturn();
    }

    private long countAuthorizations() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM oauth2_authorization", Long.class);
    }

    @Test
    void anMcpRequestWithoutATokenIsAnsweredWithTheBearerChallenge() throws Exception {
        MvcResult result = mcp(null, toolsListRequest());

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo(EXPECTED_CHALLENGE);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("search_items");
    }

    @Test
    void aValidAccessTokenReachesTheRealMcpProtocol() throws Exception {
        User user = createUser(true, true);
        saveAuthorization(createClient(user.getId()), user.getId(), "access-valid");

        MvcResult result = mcp("access-valid", toolsListRequest());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode tools = objectMapper.readTree(result.getResponse().getContentAsString()).at("/result/tools");
        assertThat(tools).hasSize(4);
        assertThat(tools.findValuesAsText("name"))
                .contains("search_items", "get_item_image", "search_boxes", "get_box_contents");
    }

    @Test
    void aSubPathOfTheMcpEndpointIsProtectedToo() throws Exception {
        User user = createUser(true, true);
        saveAuthorization(createClient(user.getId()), user.getId(), "access-subpath");

        MvcResult withoutToken = mockMvc.perform(post("/mcp/anything")
                .contentType(MediaType.APPLICATION_JSON).header("Accept", ACCEPT).content(toolsListRequest()))
                .andReturn();
        assertThat(withoutToken.getResponse().getStatus()).isEqualTo(401);
        assertThat(withoutToken.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE))
                .isEqualTo(EXPECTED_CHALLENGE);

        MvcResult withSession = mockMvc.perform(post("/mcp/anything")
                .session(sessionFor(user))
                .contentType(MediaType.APPLICATION_JSON).header("Accept", ACCEPT).content(toolsListRequest()))
                .andReturn();
        assertThat(withSession.getResponse().getStatus()).isEqualTo(401);
        assertThat(withSession.getResponse().getContentAsString()).doesNotContain("search_items");
    }

    @Test
    void aToolCallWithAValidTokenReturnsContentOverTheRealProtocol() throws Exception {
        User user = createUser(true, true);
        saveAuthorization(createClient(user.getId()), user.getId(), "access-tool-call");

        MvcResult result = mcp("access-tool-call", """
                {"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"search_items",
                "arguments":{"query":"lamp","limit":5}}}""");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.at("/result/isError").asBoolean()).isFalse();
        JsonNode content = objectMapper.readTree(response.at("/result/content/0/text").asText());
        assertThat(content.has("items")).isTrue();
    }

    @Test
    void aRefreshTokenPresentedAsBearerIsRejected() throws Exception {
        User user = createUser(true, true);
        saveAuthorization(createClient(user.getId()), user.getId(), "access-refresh-case");

        MvcResult result = mcp("refresh-access-refresh-case", toolsListRequest());

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo(EXPECTED_CHALLENGE);
    }

    @Test
    void anExpiredAccessTokenIsRejected() throws Exception {
        User user = createUser(true, true);
        RegisteredClient client = createClient(user.getId());
        authorizationService.save(authorizationBuilder(client, user.getId(), "access-expired",
                Instant.now().minus(Duration.ofMinutes(5))).build());

        MvcResult result = mcp("access-expired", toolsListRequest());

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo(EXPECTED_CHALLENGE);
    }

    @Test
    void anAccessTokenWithoutTheReadScopeIsForbidden() throws Exception {
        User user = createUser(true, true);
        RegisteredClient client = createClient(user.getId());
        authorizationService.save(authorizationBuilder(client, user.getId(), "access-no-scope",
                Instant.now().plus(Duration.ofMinutes(30))).authorizedScopes(Set.of()).build());

        MvcResult result = mcp("access-no-scope", toolsListRequest());

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE))
                .isEqualTo(EXPECTED_CHALLENGE + ", error=\"insufficient_scope\"");
    }

    @Test
    void aDisabledUserLosesEveryTokenTypeAtOnce() throws Exception {
        User user = createUser(true, true);
        RegisteredClient client = createClient(user.getId());
        saveTokensOfEveryType(client, user.getId(), "access-disabled");

        user.setEnabled(false);
        userRepository.save(user);

        assertEveryTokenTypeIsHidden("access-disabled");
        assertThat(mcp("access-disabled", toolsListRequest()).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void aUserWithoutMcpPermissionLosesEveryTokenTypeAtOnce() throws Exception {
        User user = createUser(true, true);
        RegisteredClient client = createClient(user.getId());
        saveTokensOfEveryType(client, user.getId(), "access-not-allowed");

        user.setMcpAllowed(false);
        userRepository.save(user);

        assertEveryTokenTypeIsHidden("access-not-allowed");
        assertThat(mcp("access-not-allowed", toolsListRequest()).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void aTemporaryLockDeniesButDeletesNothing() throws Exception {
        User user = createUser(true, true);
        RegisteredClient client = createClient(user.getId());
        saveTokensOfEveryType(client, user.getId(), "access-locked");
        long storedBefore = countAuthorizations();

        user.setLockedUntil(LocalDateTime.now().plusHours(1));
        userRepository.save(user);

        assertEveryTokenTypeIsHidden("access-locked");
        assertThat(mcp("access-locked", toolsListRequest()).getResponse().getStatus()).isEqualTo(401);
        assertThat(countAuthorizations()).isEqualTo(storedBefore);
        assertThat(registeredClientRepository.findById(client.getId())).isNotNull();
    }

    @Test
    void anAuthorizationWhoseClientIsBoundToAnotherUserIsHidden() throws Exception {
        User owner = createUser(true, true);
        User other = createUser(true, true);
        RegisteredClient otherUsersClient = createClient(other.getId());
        authorizationService.save(authorizationBuilder(otherUsersClient, owner.getId(), "access-wrong-binding",
                Instant.now().plus(Duration.ofMinutes(30))).build());

        assertEveryTokenTypeIsHidden("access-wrong-binding");
        assertThat(mcp("access-wrong-binding", toolsListRequest()).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void aConnectionOlderThanTheAbsoluteAgeIsHidden() throws Exception {
        User user = createUser(true, true);
        RegisteredClient client = createClient(user.getId());
        OAuth2Authorization aged = authorizationBuilder(client, user.getId(), "access-aged",
                Instant.now().plus(Duration.ofMinutes(30)))
                .attribute(McpOAuth2Attributes.STARTED_AT,
                        String.valueOf(Instant.now().minus(Duration.ofDays(15)).getEpochSecond()))
                .build();
        authorizationService.save(aged);

        assertThat(authorizationService.findById(aged.getId())).isNull();
        assertEveryTokenTypeIsHidden("access-aged");
        assertThat(mcp("access-aged", toolsListRequest()).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void anAuthorizationWithAnUnusableStartStampIsHidden() {
        User user = createUser(true, true);
        RegisteredClient client = createClient(user.getId());
        OAuth2Authorization unusable = authorizationBuilder(client, user.getId(), "access-unstamped",
                Instant.now().plus(Duration.ofMinutes(30)))
                .attribute(McpOAuth2Attributes.STARTED_AT, "not-a-timestamp")
                .build();
        authorizationService.save(unusable);

        assertThat(authorizationService.findById(unusable.getId())).isNull();
    }

    @Test
    void aBearerTokenDoesNotAuthenticateAtTheApiAndASessionDoesNotAuthenticateAtMcp() throws Exception {
        User user = createUser(true, true);
        saveAuthorization(createClient(user.getId()), user.getId(), "access-chains");

        MvcResult api = mockMvc.perform(get("/api/v1/boxes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-chains")).andReturn();
        assertThat(api.getResponse().getStatus()).isEqualTo(401);

        MockHttpSession session = sessionFor(user);
        MvcResult apiWithSession = mockMvc.perform(get("/api/v1/boxes").session(session)).andReturn();
        assertThat(apiWithSession.getResponse().getStatus()).isEqualTo(200);

        MvcResult mcpWithSession = mockMvc.perform(post("/mcp")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Accept", ACCEPT)
                .content(toolsListRequest())).andReturn();
        assertThat(mcpWithSession.getResponse().getStatus()).isEqualTo(401);
        assertThat(mcpWithSession.getResponse().getContentAsString()).doesNotContain("search_items");
    }

    @Test
    void theProtectedResourceMetadataIsReadableWithoutAToken() throws Exception {
        MvcResult result = mockMvc.perform(get("/.well-known/oauth-protected-resource/mcp")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode metadata = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(metadata.get("resource").asText()).isEqualTo("http://localhost/mcp");
        assertThat(metadata.get("authorization_servers")).hasSize(1);
        assertThat(metadata.get("authorization_servers").get(0).asText()).isEqualTo("http://localhost");
        assertThat(metadata.get("scopes_supported").get(0).asText()).isEqualTo("mcp:read");
        assertThat(metadata.get("bearer_methods_supported").get(0).asText()).isEqualTo("header");
    }

    @Test
    void malformedToolArgumentsNeverLeakInternals() throws Exception {
        User user = createUser(true, true);
        saveAuthorization(createClient(user.getId()), user.getId(), "access-malformed");

        MvcResult result = mcp("access-malformed", """
                {"jsonrpc":"2.0","id":9,"method":"tools/call","params":{"name":"search_items",
                "arguments":{"query":"lamp","limit":"abc"}}}""");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String body = result.getResponse().getContentAsString();
        assertThat(objectMapper.readTree(body).at("/result/isError").asBoolean()).isTrue();
        assertThat(body).doesNotContain("com.fasterxml", "org.springframework", "com.boxcopilot",
                "Exception", "at java.", "InvalidFormat");
    }

    private void saveTokensOfEveryType(RegisteredClient client, long userId, String accessTokenValue) {
        Instant issuedAt = Instant.now();
        authorizationService.save(authorizationBuilder(client, userId, accessTokenValue,
                issuedAt.plus(Duration.ofMinutes(30)))
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .token(new OAuth2AuthorizationCode("code-" + accessTokenValue, issuedAt,
                        issuedAt.plusSeconds(60)))
                .build());
    }

    private void assertEveryTokenTypeIsHidden(String accessTokenValue) {
        assertThat(authorizationService.findByToken(accessTokenValue, OAuth2TokenType.ACCESS_TOKEN)).isNull();
        assertThat(authorizationService.findByToken("refresh-" + accessTokenValue, OAuth2TokenType.REFRESH_TOKEN))
                .isNull();
        assertThat(authorizationService.findByToken("code-" + accessTokenValue,
                new OAuth2TokenType(OAuth2ParameterNames.CODE))).isNull();
        assertThat(authorizationService.findByToken(accessTokenValue, null)).isNull();
    }
}
