package com.boxcopilot.backend.mcp;

import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.mcp.abuse.McpFailedTokenRateLimitFilter;
import com.boxcopilot.backend.mcp.abuse.McpSubjectRateLimitFilter;
import com.boxcopilot.backend.mcp.observability.McpToolMetrics;
import com.boxcopilot.backend.mcp.security.McpOAuth2Attributes;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.server.resource.web.OAuth2ProtectedResourceMetadataFilter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** The resource server chain end to end: real chains, real MCP protocol, real database. */
class McpResourceServerIntegrationTest extends McpIntegrationTestBase {

    private static final String EXPECTED_CHALLENGE =
            "Bearer resource_metadata=\"http://localhost/.well-known/oauth-protected-resource/mcp\", "
                    + "scope=\"mcp:read\"";

    /** The {@code tools/list} capture of the Spring Boot 3.5 build (Task 0), loaded from the test classpath. */
    private static final String TOOLS_LIST_BASELINE = "baseline-3.5/85-mcp-tools-list.json";

    private static final String SEARCH_CALL = """
            {"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"search_items",
            "arguments":{"query":"lamp","limit":5}}}""";

    @Autowired
    private JsonMapper objectMapper;
    @Autowired
    private MeterRegistry meterRegistry;

    private MvcResult mcp(String bearerToken, String body) throws Exception {
        return mcp(bearerToken, body, request -> request);
    }

    private MvcResult mcp(String bearerToken, String body, UnaryOperator<MockHttpServletRequestBuilder> headers)
            throws Exception {
        var request = post("/mcp").contentType(MediaType.APPLICATION_JSON).header("Accept", ACCEPT).content(body);
        if (bearerToken != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
        }
        return mockMvc.perform(headers.apply(request)).andReturn();
    }

    private double toolCalls() {
        return meterRegistry.find(McpToolMetrics.COUNTER).counters().stream().mapToDouble(Counter::count).sum();
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
        assertThat(tools.findValuesAsString("name"))
                .contains("search_items", "get_item_image", "search_boxes", "get_box_contents");
    }

    /**
     * The tool surface is the one the Spring Boot 3.5 build exposed: the same four names, and for each the same
     * input schema (property names, types, formats, descriptions, required), title, description and annotations.
     */
    @Test
    void toolsListMatchesTheSpringBoot35Baseline() throws Exception {
        User user = createUser(true, true);
        saveAuthorization(createClient(user.getId()), user.getId(), "access-baseline");

        MvcResult result = mcp("access-baseline", toolsListRequest());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        Map<String, JsonNode> actual = byName(
                objectMapper.readTree(result.getResponse().getContentAsString()).at("/result/tools"));
        Map<String, JsonNode> baseline = byName(
                objectMapper.readTree(new String(new org.springframework.core.io.ClassPathResource(TOOLS_LIST_BASELINE).getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).at("/response/body/result/tools"));
        assertThat(baseline.keySet())
                .containsExactlyInAnyOrder("get_box_contents", "get_item_image", "search_boxes", "search_items");
        assertThat(actual.keySet()).containsExactlyInAnyOrderElementsOf(baseline.keySet());
        baseline.forEach((name, expected) -> {
            JsonNode tool = actual.get(name).deepCopy();
            // The one difference to 3.5, and a cosmetic one: Spring AI 2 declares the JSON Schema dialect, which
            // is the MCP default anyway. Everything else, properties and required included, is compared as is.
            ObjectNode inputSchema = (ObjectNode) tool.get("inputSchema");
            assertThat(inputSchema.remove("$schema").asString()).as(name + " $schema")
                    .isEqualTo("https://json-schema.org/draft/2020-12/schema");
            assertThat(inputSchema).as(name + " inputSchema").isEqualTo(expected.get("inputSchema"));
            assertThat(tool).as(name).isEqualTo(expected);
        });
    }

    private static Map<String, JsonNode> byName(JsonNode tools) {
        Map<String, JsonNode> byName = new TreeMap<>();
        tools.forEach(tool -> byName.put(tool.get("name").asString(), tool));
        assertThat(byName).hasSize(tools.size());
        return byName;
    }

    /** DNS rebinding: a page on a rebound name reaches the backend with its own {@code Host}. */
    @Test
    void aRequestForAForeignHostIsRejectedBeforeAnyToolRuns() throws Exception {
        User user = createUser(true, true);
        saveAuthorization(createClient(user.getId()), user.getId(), "access-foreign-host");
        double callsBefore = toolCalls();

        MvcResult result = mcp("access-foreign-host", SEARCH_CALL,
                request -> request.header(HttpHeaders.HOST, "attacker.example"));

        assertThat(result.getResponse().getStatus()).isEqualTo(421);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("items", "jsonrpc");
        assertThat(toolCalls()).isEqualTo(callsBefore);
    }

    @Test
    void aRequestFromAForeignOriginIsRejectedBeforeAnyToolRuns() throws Exception {
        User user = createUser(true, true);
        saveAuthorization(createClient(user.getId()), user.getId(), "access-foreign-origin");
        double callsBefore = toolCalls();

        MvcResult result = mcp("access-foreign-origin", SEARCH_CALL,
                request -> request.header(HttpHeaders.ORIGIN, "http://attacker.example"));

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("items", "jsonrpc");
        assertThat(toolCalls()).isEqualTo(callsBefore);
    }

    @Test
    void aRequestForTheConfiguredHostAndOriginReachesTheTool() throws Exception {
        User user = createUser(true, true);
        saveAuthorization(createClient(user.getId()), user.getId(), "access-own-host");
        double callsBefore = toolCalls();

        MvcResult result = mcp("access-own-host", SEARCH_CALL, request -> request
                .header(HttpHeaders.HOST, BACKEND_HOST)
                .header(HttpHeaders.ORIGIN, "http://" + BACKEND_HOST));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).at("/result/isError")
                .asBoolean()).isFalse();
        assertThat(toolCalls()).isEqualTo(callsBefore + 1);
    }

    /**
     * Pins the shape of the MCP resource-server chain, so that a filter Spring Security renames or adds in a
     * later version fails here instead of silently changing the order the rate limits rely on.
     */
    @Test
    void theResourceServerChainHasTheBearerFilterBetweenTheTwoRateLimitsAndNoFrameworkMetadataFilter() {
        List<Class<?>> filters = applicationContext.getBean("mcpSecurityFilterChain", SecurityFilterChain.class)
                .getFilters().stream().<Class<?>>map(Object::getClass).toList();

        assertThat(filters).doesNotContain(OAuth2ProtectedResourceMetadataFilter.class);
        int failedTokenLimit = filters.indexOf(McpFailedTokenRateLimitFilter.class);
        int bearer = filters.indexOf(BearerTokenAuthenticationFilter.class);
        int authorization = filters.indexOf(AuthorizationFilter.class);
        int subjectLimit = filters.indexOf(McpSubjectRateLimitFilter.class);
        assertThat(List.of(failedTokenLimit, bearer, authorization, subjectLimit)).doesNotContain(-1);
        assertThat(bearer).isEqualTo(failedTokenLimit + 1);
        assertThat(subjectLimit).isEqualTo(authorization + 1);
        assertThat(filters).containsOnlyOnce(McpFailedTokenRateLimitFilter.class, McpSubjectRateLimitFilter.class,
                BearerTokenAuthenticationFilter.class);
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
        JsonNode content = objectMapper.readTree(response.at("/result/content/0/text").asString());
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
        assertThat(metadata.get("resource").asString()).isEqualTo("http://localhost/mcp");
        assertThat(metadata.get("authorization_servers")).hasSize(1);
        assertThat(metadata.get("authorization_servers").get(0).asString()).isEqualTo("http://localhost");
        assertThat(metadata.get("scopes_supported").get(0).asString()).isEqualTo("mcp:read");
        assertThat(metadata.get("bearer_methods_supported").get(0).asString()).isEqualTo("header");
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
                "Exception", "at java.", "InvalidFormat", "tools.jackson");
        // "java.lang" is deliberately not denied yet: a wrong-typed argument still returns the library's raw message
        // ("Cannot deserialize value of type `java.lang.Integer`"), see README "Known issues". Add it once that is fixed.
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
