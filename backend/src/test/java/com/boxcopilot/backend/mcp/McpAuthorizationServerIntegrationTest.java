package com.boxcopilot.backend.mcp;

import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.mcp.authserver.McpAuthorizationServerEndpoints;
import com.boxcopilot.backend.mcp.security.McpOAuth2Attributes;
import com.boxcopilot.backend.mcp.web.McpSecureHtmlResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.nimbusds.jose.jwk.source.JWKSource;
import jakarta.servlet.http.Cookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import java.security.Principal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The authorization server end to end: real chains, real Postgres, a real browser-shaped authorization-code flow
 * with PKCE, consent, token, refresh and revocation.
 */
class McpAuthorizationServerIntegrationTest extends McpIntegrationTestBase {

    private static final String CODE_VERIFIER = "s8-code-verifier-that-is-long-enough-for-pkce-0123456789";
    private static final String CLAUDE_REDIRECT_URI = "https://claude.ai/api/mcp/auth_callback";
    private static final Pattern CSRF_INPUT =
            Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

    @Autowired
    private JsonMapper objectMapper;

    // ---------------------------------------------------------------- happy path

    @Test
    void theCompleteFlowYieldsAnAccessTokenThatReachesTheMcpEndpointAndCanBeRefreshed() throws Exception {
        User user = createUser(true, true);
        RegisteredClient client = createClient(user.getId());
        MockHttpSession session = sessionFor(user);

        String code = authorizeWithConsent(client, session);
        JsonNode tokens = redeem(code, client);

        assertThat(tokens.get("token_type").asString()).isEqualTo("Bearer");
        assertThat(tokens.get("scope").asString()).isEqualTo(McpOAuth2Attributes.SCOPE_READ);
        assertThat(tokens.get("expires_in").asLong()).isLessThanOrEqualTo(Duration.ofMinutes(30).toSeconds());
        String accessToken = tokens.get("access_token").asString();
        String refreshToken = tokens.get("refresh_token").asString();
        // An opaque reference token, not a JWT
        assertThat(accessToken).doesNotContain(".");

        MvcResult toolsList = mockMvc.perform(post(McpEndpoints.MCP)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(HttpHeaders.ACCEPT, ACCEPT)
                        .contentType(MediaType.APPLICATION_JSON).content(toolsListRequest()))
                .andReturn();
        assertThat(toolsList.getResponse().getStatus()).isEqualTo(200);
        assertThat(toolsList.getResponse().getContentAsString()).contains("search_items");

        JsonNode refreshed = objectMapper.readTree(tokenEndpoint(Map.of(
                OAuth2ParameterNames.GRANT_TYPE, "refresh_token",
                OAuth2ParameterNames.REFRESH_TOKEN, refreshToken,
                OAuth2ParameterNames.CLIENT_ID, client.getClientId())).getResponse().getContentAsString());
        assertThat(refreshed.get("access_token").asString()).isNotEqualTo(accessToken);
        assertThat(refreshed.get("refresh_token").asString()).isNotEqualTo(refreshToken);
    }

    @Test
    void theStoredAuthorizationCarriesOnlyTheLeanPrincipalAndTheConnectionStart() throws Exception {
        User user = createUser(true, true);
        RegisteredClient client = createClient(user.getId());

        authorizeWithConsent(client, sessionFor(user));

        String attributes = jdbcTemplate.queryForObject(
                "SELECT attributes FROM oauth2_authorization LIMIT 1", String.class);
        assertThat(attributes).doesNotContain("CustomUserPrincipal", "passwordHash", "OidcUser", "idToken");
        String principalName = jdbcTemplate.queryForObject(
                "SELECT principal_name FROM oauth2_authorization LIMIT 1", String.class);
        assertThat(principalName).isEqualTo(String.valueOf(user.getId()));
        assertThat(attributes).contains(McpOAuth2Attributes.STARTED_AT)
                .contains("UsernamePasswordAuthenticationToken");
        // What was written also reads back through the Jackson allowlist of the JDBC store
        Authentication restored = storedAuthorization().getAttribute(Principal.class.getName());
        assertThat(restored.getName()).isEqualTo(String.valueOf(user.getId()));
        assertThat(restored.getAuthorities()).isEmpty();
        assertThat(restored.getPrincipal())
                .isInstanceOf(org.springframework.security.core.userdetails.User.class);
    }

    /**
     * Spike item 6: with opaque reference tokens the authorization server starts and runs without any signing
     * key, so no unused in-memory key pair is generated and the JWKS endpoint has nothing to publish.
     */
    @Test
    void theAuthorizationServerRunsWithoutAnySigningKey() {
        assertThat(applicationContext.getBeansOfType(JWKSource.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(JwtEncoder.class)).isEmpty();
    }

    @Test
    void theSessionKeepsItsOwnAuthenticationAcrossTheAuthorizeRequest() throws Exception {
        User user = createUser(true, true);
        RegisteredClient client = createClient(user.getId());
        MockHttpSession session = sessionFor(user);

        authorizeWithConsent(client, session);

        assertThat(storedSecurityContext(session).getAuthentication().getPrincipal())
                .isInstanceOf(com.boxcopilot.backend.service.CustomUserDetailsService.CustomUserPrincipal.class);
    }

    // ---------------------------------------------------------------- consent page

    @Test
    void theConsentPageShowsTheUserTheScopeAndTheRedirectHostWithHardenedHeaders() throws Exception {
        User user = createUser(true, true);
        RegisteredClient client = createClient(user.getId());
        MockHttpSession session = sessionFor(user);

        MvcResult consent = consentPage(startAuthorization(client, session), session);

        assertThat(consent.getResponse().getStatus()).isEqualTo(200);
        assertThat(consent.getResponse().getHeader("Content-Security-Policy"))
                .isEqualTo(McpSecureHtmlResponse.CONTENT_SECURITY_POLICY);
        assertThat(consent.getResponse().getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(consent.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        String html = consent.getResponse().getContentAsString();
        assertThat(html).contains(user.getUsername())
                .contains("claude.ai")
                .contains("Lesezugriff auf Ihre Boxen und Gegenst")
                .contains("Nur best")
                .contains("Ablehnen");
    }

    /**
     * The consent redirect must stay on the configured backend URL. A relative consent page would make Spring
     * Authorization Server assemble the Location from the request's scheme, server name and port, all of which
     * ForwardedHeaderFilter rewrites from the forwarded headers - which would send the browser, the confidential
     * per-user client id and the consent state to whatever host the header names.
     */
    @Test
    void theConsentRedirectIgnoresForwardedHeaders() throws Exception {
        User user = createUser(true, true);
        RegisteredClient client = createClient(user.getId());

        MvcResult result = mockMvc.perform(get(authorizeUri(client.getClientId(), CLAUDE_REDIRECT_URI,
                "state-forwarded", McpOAuth2Attributes.SCOPE_READ, CODE_VERIFIER, "S256"))
                .session(sessionFor(user))
                .header("X-Forwarded-Host", "evil.example.com")
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Port", "443")).andReturn();

        assertThat(location(result))
                .startsWith("http://localhost" + McpAuthorizationServerEndpoints.CONSENT)
                .doesNotContain("evil.example.com");
    }

    /** The user gate sits in the normalization filter, which covers the consent POST as well as the authorize. */
    @Test
    void aUserWhoLosesAccessWhileTheConsentPageIsOpenCannotSubmitIt() throws Exception {
        User user = createUser(true, true);
        RegisteredClient client = createClient(user.getId());
        MockHttpSession session = sessionFor(user);
        MvcResult authorize = startAuthorization(client, session);
        MvcResult consent = consentPage(authorize, session);

        user.setMcpAllowed(false);
        userRepository.save(user);

        MvcResult submitted = mockMvc.perform(post(McpAuthorizationServerEndpoints.AUTHORIZE)
                        .session(session)
                        .cookie(csrfCookie(consent))
                        .param(OAuth2ParameterNames.CLIENT_ID, client.getClientId())
                        .param(OAuth2ParameterNames.STATE, consentState(authorize))
                        .param(OAuth2ParameterNames.SCOPE, McpOAuth2Attributes.SCOPE_READ)
                        .param("_csrf", csrfToken(consent)))
                .andReturn();

        assertThat(submitted.getResponse().getStatus()).isEqualTo(403);
        assertThat(submitted.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
    }

    @Test
    void theConsentPageIsShownAgainAfterAPreviousApproval() throws Exception {
        User user = createUser(true, true);
        RegisteredClient client = createClient(user.getId());
        MockHttpSession session = sessionFor(user);

        redeem(authorizeWithConsent(client, session), client);
        MvcResult secondAuthorize = startAuthorization(client, session);

        assertThat(location(secondAuthorize)).startsWith("http://localhost" + McpAuthorizationServerEndpoints.CONSENT);
        assertThat(jdbcTemplate.queryForList("SELECT table_name FROM information_schema.tables "
                + "WHERE table_name = 'oauth2_authorization_consent'")).isEmpty();
    }

    @Test
    void theConsentPostWithoutACsrfTokenIsRejected() throws Exception {
        User user = createUser(true, true);
        RegisteredClient client = createClient(user.getId());
        MockHttpSession session = sessionFor(user);
        String state = consentState(startAuthorization(client, session));

        MvcResult result = mockMvc.perform(post(McpAuthorizationServerEndpoints.AUTHORIZE)
                        .session(session)
                        .param(OAuth2ParameterNames.CLIENT_ID, client.getClientId())
                        .param(OAuth2ParameterNames.STATE, state)
                        .param(OAuth2ParameterNames.SCOPE, McpOAuth2Attributes.SCOPE_READ))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("code=");
    }

    @Test
    void decliningSendsAccessDeniedToTheRedirectUri() throws Exception {
        User user = createUser(true, true);
        RegisteredClient client = createClient(user.getId());
        MockHttpSession session = sessionFor(user);
        MvcResult authorize = startAuthorization(client, session);
        MvcResult consent = consentPage(authorize, session);

        MvcResult declined = mockMvc.perform(post(McpAuthorizationServerEndpoints.AUTHORIZE)
                        .session(session)
                        .cookie(csrfCookie(consent))
                        .param(OAuth2ParameterNames.CLIENT_ID, client.getClientId())
                        .param(OAuth2ParameterNames.STATE, consentState(authorize))
                        .param("_csrf", csrfToken(consent)))
                .andReturn();

        assertThat(declined.getResponse().getStatus()).isEqualTo(302);
        assertThat(location(declined)).startsWith(CLAUDE_REDIRECT_URI).contains("error=access_denied")
                .doesNotContain("code=");
    }

    // ---------------------------------------------------------------- PKCE, codes, redirect URIs

    @Test
    void anAuthorizationCodeCannotBeRedeemedTwice() throws Exception {
        User user = createUser(true, true);
        RegisteredClient client = createClient(user.getId());
        String code = authorizeWithConsent(client, sessionFor(user));

        redeem(code, client);
        MvcResult second = tokenEndpoint(authorizationCodeParameters(code, client.getClientId(), CODE_VERIFIER));

        assertThat(second.getResponse().getStatus()).isEqualTo(400);
        assertThat(second.getResponse().getContentAsString()).contains("invalid_grant");
    }

    @Test
    void theAuthorizationCodeLivesForSixtySeconds() throws Exception {
        User user = createUser(true, true);
        RegisteredClient client = createClient(user.getId());

        authorizeWithConsent(client, sessionFor(user));

        assertThat(client.getTokenSettings().getAuthorizationCodeTimeToLive()).isEqualTo(Duration.ofSeconds(60));
        OAuth2Authorization.Token<OAuth2AuthorizationCode> storedCode = storedAuthorization().getToken(
                OAuth2AuthorizationCode.class);
        assertThat(Duration.between(storedCode.getToken().getIssuedAt(), storedCode.getToken().getExpiresAt()))
                .isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void anExpiredAuthorizationCodeIsRejected() throws Exception {
        User user = createUser(true, true);
        RegisteredClient client = createClient(user.getId());
        String code = authorizeWithConsent(client, sessionFor(user));

        jdbcTemplate.update("UPDATE oauth2_authorization SET authorization_code_issued_at = ?, "
                        + "authorization_code_expires_at = ?",
                java.sql.Timestamp.valueOf(LocalDateTime.now().minusMinutes(2)),
                java.sql.Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)));

        MvcResult result = tokenEndpoint(authorizationCodeParameters(code, client.getClientId(), CODE_VERIFIER));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("invalid_grant");
    }

    @Test
    void aWrongCodeVerifierIsRejected() throws Exception {
        User user = createUser(true, true);
        RegisteredClient client = createClient(user.getId());
        String code = authorizeWithConsent(client, sessionFor(user));

        MvcResult result = tokenEndpoint(
                authorizationCodeParameters(code, client.getClientId(), CODE_VERIFIER + "-other"));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void aTokenRequestWithoutACodeVerifierIsNotAuthenticatedAsAClient() throws Exception {
        User user = createUser(true, true);
        RegisteredClient client = createClient(user.getId());
        String code = authorizeWithConsent(client, sessionFor(user));

        MvcResult result = tokenEndpoint(Map.of(
                OAuth2ParameterNames.GRANT_TYPE, "authorization_code",
                OAuth2ParameterNames.CODE, code,
                OAuth2ParameterNames.REDIRECT_URI, CLAUDE_REDIRECT_URI,
                OAuth2ParameterNames.CLIENT_ID, client.getClientId()));

        // Without the PKCE proof the public client is not authenticated at all, so the chain answers 401
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("access_token");
    }

    @Test
    void plainPkceAndAMissingCodeChallengeAreRejectedAtTheAuthorizeEndpoint() throws Exception {
        User user = createUser(true, true);
        RegisteredClient client = createClient(user.getId());
        MockHttpSession session = sessionFor(user);

        MvcResult plain = mockMvc.perform(get(authorizeUri(client.getClientId(), CLAUDE_REDIRECT_URI, "state-plain",
                McpOAuth2Attributes.SCOPE_READ, CODE_VERIFIER, "plain")).session(session)).andReturn();
        MvcResult missing = mockMvc.perform(get(authorizeUri(client.getClientId(), CLAUDE_REDIRECT_URI,
                "state-missing", McpOAuth2Attributes.SCOPE_READ, null, null)).session(session)).andReturn();

        // The error goes back to the validated redirect URI, as RFC 6749 requires, and carries no code
        assertNoAuthorization(plain);
        assertNoAuthorization(missing);
    }

    @Test
    void onlyTheExactlyRegisteredRedirectUriIsAccepted() throws Exception {
        User user = createUser(true, true);
        RegisteredClient client = createClient(user.getId());
        MockHttpSession session = sessionFor(user);
        List<String> rejected = List.of(
                CLAUDE_REDIRECT_URI + "/extra",
                CLAUDE_REDIRECT_URI + "?x=1",
                CLAUDE_REDIRECT_URI + "/",
                "https://evil.example.com/api/mcp/auth_callback",
                // RFC 8252 port flexibility is deliberately not granted: a different loopback port is rejected
                "http://127.0.0.1:41001/callback");

        for (String redirectUri : rejected) {
            MvcResult result = mockMvc.perform(get(authorizeUri(client.getClientId(), redirectUri, "state-redirect",
                    McpOAuth2Attributes.SCOPE_READ, CODE_VERIFIER, "S256")).session(session)).andReturn();

            assertThat(result.getResponse().getStatus()).as(redirectUri).isEqualTo(400);
            assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).as(redirectUri).isNull();
        }
    }

    @Test
    void theRegisteredLoopbackRedirectUriWithItsOwnPortIsAccepted() throws Exception {
        User user = createUser(true, true);
        RegisteredClient client = createClient(user.getId());

        MvcResult result = mockMvc.perform(get(authorizeUri(client.getClientId(), "http://127.0.0.1:41000/callback",
                "state-loopback", McpOAuth2Attributes.SCOPE_READ, CODE_VERIFIER, "S256"))
                .session(sessionFor(user))).andReturn();

        assertThat(location(result)).startsWith("http://localhost" + McpAuthorizationServerEndpoints.CONSENT);
    }

    /**
     * A client with a single registered redirect URI may leave {@code redirect_uri} out - unless the request
     * asks for OpenID Connect, which requires it outright. That rule came with the displaced default validator
     * and is kept here, even though {@code openid} is never a registered scope today.
     */
    @Test
    void omittingTheRedirectUriIsOnlyAllowedWithoutOpenIdConnect() throws Exception {
        User user = createUser(true, true);
        RegisteredClient client = RegisteredClientFixtures
                .publicClient(user.getId(), List.of(CLAUDE_REDIRECT_URI));
        registeredClientRepository.save(client);
        MockHttpSession session = sessionFor(user);

        MvcResult accepted = mockMvc.perform(get(authorizeUri(client.getClientId(), null, "state-no-redirect",
                McpOAuth2Attributes.SCOPE_READ, CODE_VERIFIER, "S256")).session(session)).andReturn();
        MvcResult rejected = mockMvc.perform(get(authorizeUri(client.getClientId(), null, "state-openid",
                "openid", CODE_VERIFIER, "S256")).session(session)).andReturn();

        assertThat(location(accepted)).startsWith("http://localhost" + McpAuthorizationServerEndpoints.CONSENT);
        assertThat(rejected.getResponse().getStatus()).isEqualTo(400);
        assertThat(rejected.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
    }

    @Test
    void anAuthorizeRequestWithoutAScopeFallsBackToTheSingleScope() throws Exception {
        User user = createUser(true, true);
        RegisteredClient client = createClient(user.getId());

        MvcResult result = mockMvc.perform(get(authorizeUri(client.getClientId(), CLAUDE_REDIRECT_URI, "state-noscope",
                null, CODE_VERIFIER, "S256")).session(sessionFor(user))).andReturn();

        assertThat(location(result)).contains("scope=mcp:read");
    }

    @Test
    void aResourceParameterIsIgnoredRatherThanRejected() throws Exception {
        User user = createUser(true, true);
        RegisteredClient client = createClient(user.getId());

        MvcResult result = mockMvc.perform(get(java.net.URI.create(authorizeUrl(client.getClientId(),
                CLAUDE_REDIRECT_URI, "state-resource", McpOAuth2Attributes.SCOPE_READ, CODE_VERIFIER, "S256")
                + "&resource=" + encode("http://localhost/mcp"))).session(sessionFor(user))).andReturn();

        assertThat(location(result)).startsWith("http://localhost" + McpAuthorizationServerEndpoints.CONSENT);
    }

    // ---------------------------------------------------------------- client binding and user state

    @Test
    void anotherUsersClientCannotBeAuthorized() throws Exception {
        User owner = createUser(true, true);
        User attacker = createUser(true, true);
        RegisteredClient client = createClient(owner.getId());

        MvcResult result = mockMvc.perform(get(authorizeUri(client.getClientId(), CLAUDE_REDIRECT_URI, "state-bind",
                McpOAuth2Attributes.SCOPE_READ, CODE_VERIFIER, "S256")).session(sessionFor(attacker))).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("code=");
    }

    /**
     * The validator runs before Spring Authorization Server looks at the login, so it must let an anonymous
     * request pass; otherwise no one could ever reach the login page. The binding is checked on the replay.
     */
    @Test
    void anAnonymousAuthorizeOfAnotherUsersClientReachesTheEntryPointInsteadOfBeingDenied() throws Exception {
        User owner = createUser(true, true);
        RegisteredClient client = createClient(owner.getId());

        MvcResult result = mockMvc.perform(get(authorizeUri(client.getClientId(), CLAUDE_REDIRECT_URI,
                "state-anonymous", McpOAuth2Attributes.SCOPE_READ, CODE_VERIFIER, "S256"))).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(302);
        assertThat(location(result)).isEqualTo("http://localhost:3000/");
        assertThat(result.getResponse().getCookie("mcp_authz")).isNotNull();
    }

    @Test
    void aUserWhoMayNotUseMcpIsRejectedAtTheAuthorizeEndpoint() throws Exception {
        for (UserState state : UserState.values()) {
            User user = createUser(true, true);
            RegisteredClient client = createClient(user.getId());
            state.applyTo(user);
            userRepository.save(user);

            MvcResult result = mockMvc.perform(get(authorizeUri(client.getClientId(), CLAUDE_REDIRECT_URI,
                    "state-" + state, McpOAuth2Attributes.SCOPE_READ, CODE_VERIFIER, "S256"))
                    .session(sessionFor(user))).andReturn();

            assertThat(result.getResponse().getStatus()).as("%s", state).isEqualTo(403);
            assertThat(result.getResponse().getContentAsString()).as("%s", state).doesNotContain("code=");
        }
    }

    @Test
    void aUserWhoLosesAccessBetweenConsentAndRedemptionCannotRedeemTheCode() throws Exception {
        User user = createUser(true, true);
        RegisteredClient client = createClient(user.getId());
        String code = authorizeWithConsent(client, sessionFor(user));

        user.setMcpAllowed(false);
        userRepository.save(user);

        MvcResult result = tokenEndpoint(authorizationCodeParameters(code, client.getClientId(), CODE_VERIFIER));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("invalid_grant");
    }

    @Test
    void aUserWhoLosesAccessAfterTheTokenIssueCannotRefresh() throws Exception {
        User user = createUser(true, true);
        RegisteredClient client = createClient(user.getId());
        JsonNode tokens = redeem(authorizeWithConsent(client, sessionFor(user)), client);

        user.setLockedUntil(LocalDateTime.now().plusHours(1));
        userRepository.save(user);

        MvcResult result = refresh(tokens.get("refresh_token").asString(), client.getClientId());

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("invalid_grant");
    }

    // ---------------------------------------------------------------- refresh rotation and absolute age

    @Test
    void aRotatedRefreshTokenInvalidatesTheOldOneAndKeepsTheConnectionStart() throws Exception {
        User user = createUser(true, true);
        RegisteredClient client = createClient(user.getId());
        JsonNode tokens = redeem(authorizeWithConsent(client, sessionFor(user)), client);
        String startedAt = startedAt();

        String refreshToken = tokens.get("refresh_token").asString();
        JsonNode refreshed = objectMapper.readTree(
                refresh(refreshToken, client.getClientId()).getResponse().getContentAsString());

        assertThat(startedAt()).isEqualTo(startedAt);
        assertThat(refresh(refreshToken, client.getClientId()).getResponse().getStatus()).isEqualTo(400);
        assertThat(refresh(refreshed.get("refresh_token").asString(), client.getClientId()).getResponse().getStatus())
                .isEqualTo(200);
    }

    @Test
    void aConnectionOlderThanTheAbsoluteAgeCannotBeRefreshed() throws Exception {
        User user = createUser(true, true);
        RegisteredClient client = createClient(user.getId());
        JsonNode tokens = redeem(authorizeWithConsent(client, sessionFor(user)), client);

        ageTheConnectionBeyondTheLimit();

        MvcResult result = refresh(tokens.get("refresh_token").asString(), client.getClientId());

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("invalid_grant");
    }

    // ---------------------------------------------------------------- revocation

    @Test
    void aPublicClientCanRevokeItsOwnAccessToken() throws Exception {
        User user = createUser(true, true);
        RegisteredClient client = createClient(user.getId());
        JsonNode tokens = redeem(authorizeWithConsent(client, sessionFor(user)), client);
        String accessToken = tokens.get("access_token").asString();

        MvcResult revoked = mockMvc.perform(form(McpAuthorizationServerEndpoints.REVOKE, Map.of(
                OAuth2ParameterNames.TOKEN, accessToken,
                OAuth2ParameterNames.CLIENT_ID, client.getClientId()))).andReturn();

        assertThat(revoked.getResponse().getStatus()).isEqualTo(200);
        MvcResult afterwards = mockMvc.perform(post(McpEndpoints.MCP)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(HttpHeaders.ACCEPT, ACCEPT)
                        .contentType(MediaType.APPLICATION_JSON).content(toolsListRequest()))
                .andReturn();
        assertThat(afterwards.getResponse().getStatus()).isEqualTo(401);
    }

    /**
     * The provider reads whether a request reached the revocation endpoint from the marker the converter sets
     * out of the matched endpoint. A client that sends that parameter itself cannot turn a token request into a
     * revocation, because the converter strips it before recording what the server observed.
     */
    @Test
    void theRevocationMarkerCannotBeSuppliedByTheClient() throws Exception {
        User user = createUser(true, true);
        RegisteredClient client = createClient(user.getId());
        JsonNode tokens = redeem(authorizeWithConsent(client, sessionFor(user)), client);
        String accessToken = tokens.get("access_token").asString();

        MvcResult forged = mockMvc.perform(form(McpAuthorizationServerEndpoints.TOKEN, Map.of(
                OAuth2ParameterNames.TOKEN, accessToken,
                OAuth2ParameterNames.CLIENT_ID, client.getClientId(),
                "boxpilot.revocation_request", "true"))).andReturn();

        assertThat(forged.getResponse().getStatus() / 100).isNotEqualTo(2);
        MvcResult stillWorking = mockMvc.perform(post(McpEndpoints.MCP)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(HttpHeaders.ACCEPT, ACCEPT)
                        .contentType(MediaType.APPLICATION_JSON).content(toolsListRequest()))
                .andReturn();
        assertThat(stillWorking.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void anotherUsersClientCannotRevokeAToken() throws Exception {
        User owner = createUser(true, true);
        User attacker = createUser(true, true);
        RegisteredClient ownerClient = createClient(owner.getId());
        RegisteredClient attackerClient = createClient(attacker.getId());
        JsonNode tokens = redeem(authorizeWithConsent(ownerClient, sessionFor(owner)), ownerClient);
        String accessToken = tokens.get("access_token").asString();

        mockMvc.perform(form(McpAuthorizationServerEndpoints.REVOKE, Map.of(
                OAuth2ParameterNames.TOKEN, accessToken,
                OAuth2ParameterNames.CLIENT_ID, attackerClient.getClientId()))).andReturn();

        MvcResult stillWorking = mockMvc.perform(post(McpEndpoints.MCP)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(HttpHeaders.ACCEPT, ACCEPT)
                        .contentType(MediaType.APPLICATION_JSON).content(toolsListRequest()))
                .andReturn();
        assertThat(stillWorking.getResponse().getStatus()).isEqualTo(200);
    }

    // ---------------------------------------------------------------- metadata and reachable endpoints

    @Test
    void theMetadataAdvertisesTheConfiguredIssuerEvenBehindForwardedHeaders() throws Exception {
        MvcResult result = mockMvc.perform(get(McpAuthorizationServerEndpoints.METADATA)
                        .header("X-Forwarded-Host", "evil.example.com")
                        .header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-Port", "443"))
                .andReturn();

        JsonNode metadata = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(metadata.get("issuer").asString()).isEqualTo("http://localhost");
        assertThat(metadata.get("authorization_endpoint").asString())
                .isEqualTo("http://localhost" + McpAuthorizationServerEndpoints.AUTHORIZE);
        assertThat(metadata.get("token_endpoint").asString())
                .isEqualTo("http://localhost" + McpAuthorizationServerEndpoints.TOKEN);
        assertThat(metadata.get("revocation_endpoint").asString())
                .isEqualTo("http://localhost" + McpAuthorizationServerEndpoints.REVOKE);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("evil.example.com");
    }

    @Test
    void theMetadataAdvertisesOnlyWhatIsActuallyServed() throws Exception {
        MvcResult result = mockMvc.perform(get(McpAuthorizationServerEndpoints.METADATA)).andReturn();

        JsonNode metadata = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(metadata.has("introspection_endpoint")).isFalse();
        assertThat(metadata.has("device_authorization_endpoint")).isFalse();
        assertThat(metadata.has("pushed_authorization_request_endpoint")).isFalse();
        assertThat(metadata.has("registration_endpoint")).isFalse();
        assertThat(metadata.has("jwks_uri")).isFalse();
        assertThat(values(metadata, "grant_types_supported"))
                .containsExactlyInAnyOrder("authorization_code", "refresh_token");
        assertThat(values(metadata, "scopes_supported")).containsExactly("mcp:read");
        assertThat(values(metadata, "token_endpoint_auth_methods_supported")).containsExactly("none");
        assertThat(values(metadata, "code_challenge_methods_supported")).containsExactly("S256");
    }

    @Test
    void theUnneededAuthorizationServerEndpointsAreUnreachable() throws Exception {
        List<String> unreachable = List.of("/oauth2/introspect", "/oauth2/device_authorization", "/oauth2/par",
                "/oauth2/device_verification", "/oauth2/jwks", "/connect/register", "/connect/logout", "/userinfo",
                "/.well-known/openid-configuration");

        for (String path : unreachable) {
            MvcResult withoutSession = mockMvc.perform(get(path)).andReturn();
            MvcResult asPost = mockMvc.perform(post(path).with(request -> {
                request.setRemoteAddr("127.0.0.1");
                return request;
            })).andReturn();

            assertThat(withoutSession.getResponse().getStatus() / 100).as(path).isNotEqualTo(2);
            assertThat(asPost.getResponse().getStatus() / 100).as(path).isNotEqualTo(2);
            assertThat(withoutSession.getResponse().getContentAsString()).as(path)
                    .doesNotContain("keys", "active", "device_code", "client_id_issued_at");
        }
    }

    @Test
    void anUnauthenticatedTokenOrRevocationRequestIsAnsweredWithAnErrorRatherThanALoginRedirect() throws Exception {
        for (String path : List.of(McpAuthorizationServerEndpoints.TOKEN, McpAuthorizationServerEndpoints.REVOKE)) {
            MvcResult result = mockMvc.perform(post(path)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)).andReturn();

            assertThat(result.getResponse().getStatus()).as(path).isEqualTo(401);
            assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).as(path).isNull();
            assertThat(result.getResponse().getCookie("mcp_authz")).as(path).isNull();
        }
    }

    // ---------------------------------------------------------------- helpers

    private enum UserState {
        DISABLED {
            @Override
            void applyTo(User user) {
                user.setEnabled(false);
            }
        },
        LOCKED {
            @Override
            void applyTo(User user) {
                user.setLockedUntil(LocalDateTime.now().plusHours(1));
            }
        },
        NOT_ALLOWED {
            @Override
            void applyTo(User user) {
                user.setMcpAllowed(false);
            }
        };

        abstract void applyTo(User user);
    }

    private MvcResult startAuthorization(RegisteredClient client, MockHttpSession session) throws Exception {
        return mockMvc.perform(get(authorizeUri(client.getClientId(), CLAUDE_REDIRECT_URI, "client-state",
                McpOAuth2Attributes.SCOPE_READ, CODE_VERIFIER, "S256")).session(session)).andReturn();
    }

    private MvcResult consentPage(MvcResult authorize, MockHttpSession session) throws Exception {
        return mockMvc.perform(get(McpAuthorizationServerEndpoints.CONSENT)
                .param(OAuth2ParameterNames.STATE, consentState(authorize)).session(session)).andReturn();
    }

    /** Runs authorize, consent page and consent POST exactly as a browser would, and returns the code. */
    private String authorizeWithConsent(RegisteredClient client, MockHttpSession session) throws Exception {
        MvcResult authorize = startAuthorization(client, session);
        MvcResult consent = consentPage(authorize, session);

        MvcResult approved = mockMvc.perform(post(McpAuthorizationServerEndpoints.AUTHORIZE)
                        .session(session)
                        .cookie(csrfCookie(consent))
                        .param(OAuth2ParameterNames.CLIENT_ID, client.getClientId())
                        .param(OAuth2ParameterNames.STATE, consentState(authorize))
                        .param(OAuth2ParameterNames.SCOPE, McpOAuth2Attributes.SCOPE_READ)
                        .param("_csrf", csrfToken(consent)))
                .andReturn();

        assertThat(approved.getResponse().getStatus()).isEqualTo(302);
        String redirect = location(approved);
        assertThat(redirect).startsWith(CLAUDE_REDIRECT_URI).contains("state=client-state");
        return queryParameter(redirect, OAuth2ParameterNames.CODE);
    }

    private JsonNode redeem(String code, RegisteredClient client) throws Exception {
        MvcResult result = tokenEndpoint(authorizationCodeParameters(code, client.getClientId(), CODE_VERIFIER));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private MvcResult refresh(String refreshToken, String clientId) throws Exception {
        return tokenEndpoint(Map.of(OAuth2ParameterNames.GRANT_TYPE, "refresh_token",
                OAuth2ParameterNames.REFRESH_TOKEN, refreshToken, OAuth2ParameterNames.CLIENT_ID, clientId));
    }

    private MvcResult tokenEndpoint(Map<String, String> parameters) throws Exception {
        return mockMvc.perform(form(McpAuthorizationServerEndpoints.TOKEN, parameters)).andReturn();
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder form(
            String path, Map<String, String> parameters) {
        var builder = post(path).contentType(MediaType.APPLICATION_FORM_URLENCODED);
        parameters.forEach(builder::param);
        return builder;
    }

    private static Map<String, String> authorizationCodeParameters(String code, String clientId,
                                                                   String codeVerifier) {
        return Map.of(OAuth2ParameterNames.GRANT_TYPE, "authorization_code",
                OAuth2ParameterNames.CODE, code,
                OAuth2ParameterNames.REDIRECT_URI, CLAUDE_REDIRECT_URI,
                OAuth2ParameterNames.CLIENT_ID, clientId,
                "code_verifier", codeVerifier);
    }

    /**
     * A ready-made {@link java.net.URI}, not a template: MockMvc encodes a URL template again, which would turn
     * the percent signs of an encoded {@code redirect_uri} into {@code %25}.
     */
    private static java.net.URI authorizeUri(String clientId, String redirectUri, String state, String scope,
                                             String codeVerifier, String challengeMethod) {
        return java.net.URI.create(authorizeUrl(clientId, redirectUri, state, scope, codeVerifier, challengeMethod));
    }

    private static String authorizeUrl(String clientId, String redirectUri, String state, String scope,
                                       String codeVerifier, String challengeMethod) {
        StringBuilder url = new StringBuilder(McpAuthorizationServerEndpoints.AUTHORIZE)
                .append("?response_type=code")
                .append("&client_id=").append(encode(clientId))
                .append("&state=").append(encode(state));
        if (redirectUri != null) {
            url.append("&redirect_uri=").append(encode(redirectUri));
        }
        if (scope != null) {
            url.append("&scope=").append(encode(scope));
        }
        if (codeVerifier != null && challengeMethod != null) {
            String challenge = "S256".equals(challengeMethod)
                    ? RegisteredClientFixtures.codeChallenge(codeVerifier) : codeVerifier;
            url.append("&code_challenge=").append(encode(challenge))
                    .append("&code_challenge_method=").append(encode(challengeMethod));
        }
        return url.toString();
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static List<String> values(JsonNode metadata, String claim) {
        return metadata.get(claim).valueStream().map(JsonNode::asString).toList();
    }

    /** A rejected authorization must not hand out a code, whether it answers with 400 or redirects the error. */
    private static void assertNoAuthorization(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus() / 100).isNotEqualTo(2);
        String location = location(result);
        if (location != null) {
            assertThat(location).contains("error=").doesNotContain("code=");
        }
        assertThat(result.getResponse().getContentAsString()).doesNotContain("code=");
    }

    private static String location(MvcResult result) {
        return result.getResponse().getHeader(HttpHeaders.LOCATION);
    }

    private static String consentState(MvcResult authorize) throws Exception {
        assertThat(authorize.getResponse().getStatus())
                .as("authorize response: %s %s", authorize.getResponse().getErrorMessage(),
                        authorize.getResponse().getContentAsString())
                .isEqualTo(302);
        return queryParameter(location(authorize), OAuth2ParameterNames.STATE);
    }

    /** The values are percent-encoded in the redirect URL, and a Base64 state or code ends in {@code %3D}. */
    private static String queryParameter(String url, String name) {
        String value = UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst(name);
        return value == null ? null : java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    /** Reads the token out of the rendered form, exactly as a browser would, and undoes the HTML escaping. */
    private static String csrfToken(MvcResult consent) throws Exception {
        Matcher matcher = CSRF_INPUT.matcher(consent.getResponse().getContentAsString());
        assertThat(matcher.find()).as("consent page carries a CSRF token").isTrue();
        return matcher.group(1).replace("&#39;", "'").replace("&quot;", "\"")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }

    private static Cookie csrfCookie(MvcResult consent) {
        Cookie cookie = consent.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).as("consent page issues the CSRF cookie").isNotNull();
        return cookie;
    }

    private OAuth2Authorization storedAuthorization() {
        String id = jdbcTemplate.queryForObject("SELECT id FROM oauth2_authorization LIMIT 1", String.class);
        return authorizationService.findById(id);
    }

    private String startedAt() {
        return storedAuthorization().getAttribute(McpOAuth2Attributes.STARTED_AT);
    }

    private void ageTheConnectionBeyondTheLimit() {
        String old = String.valueOf(java.time.Instant.now().minus(Duration.ofDays(15)).getEpochSecond());
        String attributes = jdbcTemplate.queryForObject(
                "SELECT attributes FROM oauth2_authorization LIMIT 1", String.class);
        String aged = attributes.replaceFirst("(\"" + McpOAuth2Attributes.STARTED_AT + "\"\\s*:\\s*\")\\d+(\")",
                "$1" + old + "$2");
        assertThat(aged).as("start timestamp rewritten").isNotEqualTo(attributes);
        jdbcTemplate.update("UPDATE oauth2_authorization SET attributes = ?", aged);
    }
}
