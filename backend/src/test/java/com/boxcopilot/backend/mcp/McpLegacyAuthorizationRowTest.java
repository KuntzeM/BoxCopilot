package com.boxcopilot.backend.mcp;

import com.boxcopilot.backend.mcp.security.McpOAuth2Attributes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MvcResult;

import java.security.Principal;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * An {@code oauth2_authorization} row written by Spring Authorization Server 1.5 on Boot 3.5 (Jackson 2 type
 * info in {@code attributes} and the token metadata), read by Boot 4 / Spring Security 7.
 *
 * <p>The fixture is {@code docs/superpowers/plans/artifacts/baseline-3.5/legacy-authorization-row.sql}, copied
 * byte for byte to {@code src/test/resources/mcp/}: the registered client and the authorization the real
 * authorization-code + refresh flow left behind (throwaway values, see the baseline README).
 *
 * <p><b>Observed on Spring Security 7.1.2:</b> the rows load. The default
 * {@code JdbcOAuth2AuthorizationService} row mapper (Jackson 3, {@code SecurityJacksonModules}) and
 * {@code JdbcRegisteredClientRepository} read the Jackson 2 JSON with subject, scopes, client, tokens, the
 * connection start and the stored principal unchanged; the resource server accepts the access token once its
 * validity window is current and answers a plain {@code 401} (never a 500) once it has expired. No compatibility
 * row mapper and no invalidating migration is therefore needed: existing Claude connections survive the
 * upgrade. Should a later version stop reading such rows, these tests fail and the decision (a Jackson 2 mapper
 * if one is still supported, else a migration deleting the rows) has to be made again.
 *
 * <p>Only the timestamp columns are touched (the token expiry, to make the token current); the serialised JSON
 * is never edited. The clock is pinned two minutes after the stored connection start so that the 14-day
 * connection age does not turn the test red as the fixture ages.
 */
class McpLegacyAuthorizationRowTest extends McpIntegrationTestBase {

    private static final String REGISTERED_CLIENT_ID = "f128fb37-084b-4b3d-bef3-965a43b1fc9e";
    private static final String CLIENT_ID = "OJJcyLjCRcKu5v2myMtAfXE2Tr0Hix4NeIO19IEOwow";
    private static final String AUTHORIZATION_ID = "4283e320-2ab0-4be4-9d0f-96dbb9d1aa03";
    private static final String ACCESS_TOKEN = "8nzpYrFWUfY2XfuOJeCtNxwgro6ih6fpZYX3iCJ-O3NnjKTZWyPkr1aQ2d1uy_v_QO6Qq"
            + "-S-gFccM6CjZxlzWsNQsPycnWZdY2IpFK-NdXbJ1OZd3zltwRhLOfOYTSHP";
    private static final String REFRESH_TOKEN = "R2ywOk-YszxlspD8cvjEr0pAZZtAuB0-ZDFSdAxVYmi6Y9tuS2SpgHoOjyoVSbcd5p02T"
            + "sm5p5xCl7SEjWC6RjMf-IPuSfhQIJON4VmvEFSLjvJFHY1J619N0znuJaSD";
    private static final long LEGACY_USER_ID = 2;
    private static final long STARTED_AT = 1793530674L;

    @TestBean
    Clock clock;

    static Clock clock() {
        return Clock.fixed(Instant.ofEpochSecond(STARTED_AT).plus(Duration.ofMinutes(2)), ZoneOffset.UTC);
    }

    /** The subject of the fixture is user id 2, so that row has to exist and be allowed to use MCP. */
    @BeforeEach
    void loadLegacyRows() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO users (id, username, name, auth_provider, role, enabled, created_at, mcp_allowed)
                VALUES (?, 'mcp-test-user-legacy', 'Legacy', 'LOCAL', 'USER', TRUE, CURRENT_TIMESTAMP, TRUE)
                ON CONFLICT (id) DO UPDATE SET username = EXCLUDED.username, enabled = TRUE, mcp_allowed = TRUE,
                    locked_until = NULL, failed_login_attempts = 0""", LEGACY_USER_ID);
        // An explicit id must not leave the sequence behind it, or a later insert would collide with it.
        jdbcTemplate.execute("SELECT setval(pg_get_serial_sequence('users', 'id'), (SELECT MAX(id) FROM users))");
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("mcp/legacy-authorization-row.sql"));
        }
    }

    @Test
    void theLegacyClientRowLoadsUnchanged() {
        RegisteredClient client = registeredClientRepository.findById(REGISTERED_CLIENT_ID);

        assertThat(client).isNotNull();
        assertThat(client.getClientId()).isEqualTo(CLIENT_ID);
        assertThat(client.getClientAuthenticationMethods()).containsExactly(ClientAuthenticationMethod.NONE);
        assertThat(client.getAuthorizationGrantTypes()).containsExactlyInAnyOrder(
                AuthorizationGrantType.AUTHORIZATION_CODE, AuthorizationGrantType.REFRESH_TOKEN);
        assertThat(client.getRedirectUris()).containsExactly("http://localhost:41000/callback");
        assertThat(client.getScopes()).containsExactly(McpOAuth2Attributes.SCOPE_READ);
        assertThat(client.getClientSettings().<Object>getSetting(McpOAuth2Attributes.CLIENT_USER_ID))
                .isEqualTo(String.valueOf(LEGACY_USER_ID));
        assertThat(client.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(client.getTokenSettings().getAccessTokenFormat()).isEqualTo(OAuth2TokenFormat.REFERENCE);
        assertThat(client.getTokenSettings().getAccessTokenTimeToLive()).isEqualTo(Duration.ofMinutes(30));
        assertThat(client.getTokenSettings().getRefreshTokenTimeToLive()).isEqualTo(Duration.ofDays(7));
        assertThat(client.getTokenSettings().isReuseRefreshTokens()).isFalse();
    }

    @Test
    void theLegacyAuthorizationLoadsThroughTheProductionServiceWithItsOriginalData() {
        OAuth2Authorization authorization = authorizationService.findByToken(ACCESS_TOKEN, OAuth2TokenType.ACCESS_TOKEN);

        assertThat(authorization).isNotNull();
        assertThat(authorization.getId()).isEqualTo(AUTHORIZATION_ID);
        assertThat(authorization.getPrincipalName()).isEqualTo(String.valueOf(LEGACY_USER_ID));
        assertThat(authorization.getRegisteredClientId()).isEqualTo(REGISTERED_CLIENT_ID);
        assertThat(authorization.getAuthorizationGrantType()).isEqualTo(AuthorizationGrantType.AUTHORIZATION_CODE);
        assertThat(authorization.getAuthorizedScopes()).containsExactly(McpOAuth2Attributes.SCOPE_READ);
        assertThat(authorization.<String>getAttribute(McpOAuth2Attributes.STARTED_AT))
                .isEqualTo(String.valueOf(STARTED_AT));

        OAuth2AccessToken accessToken = authorization.getAccessToken().getToken();
        assertThat(accessToken.getTokenType()).isEqualTo(OAuth2AccessToken.TokenType.BEARER);
        assertThat(accessToken.getScopes()).containsExactly(McpOAuth2Attributes.SCOPE_READ);
        assertThat(authorization.getAccessToken().getClaims()).containsEntry("sub", String.valueOf(LEGACY_USER_ID));
        assertThat(authorization.getRefreshToken().getToken().getTokenValue()).isEqualTo(REFRESH_TOKEN);
        assertThat(authorization.getToken(OAuth2AuthorizationCode.class).isInvalidated()).isTrue();

        Authentication principal = authorization.getAttribute(Principal.class.getName());
        assertThat(principal.getName()).isEqualTo(String.valueOf(LEGACY_USER_ID));
        assertThat(principal.getAuthorities()).isEmpty();
        OAuth2AuthorizationRequest request = authorization.getAttribute(OAuth2AuthorizationRequest.class.getName());
        assertThat(request.getClientId()).isEqualTo(CLIENT_ID);
        assertThat(request.getRedirectUri()).isEqualTo("http://localhost:41000/callback");
        assertThat(request.getScopes()).isEqualTo(Set.of(McpOAuth2Attributes.SCOPE_READ));

        assertThat(authorizationService.findById(AUTHORIZATION_ID)).isNotNull();
        assertThat(authorizationService.findByToken(REFRESH_TOKEN, OAuth2TokenType.REFRESH_TOKEN).getId())
                .isEqualTo(AUTHORIZATION_ID);
    }

    @Test
    void theResourceServerAcceptsTheLegacyAccessTokenWhileItIsValid() throws Exception {
        Instant now = Instant.now();
        jdbcTemplate.update("UPDATE oauth2_authorization SET access_token_expires_at = ?, "
                        + "refresh_token_expires_at = ? WHERE id = ?",
                Timestamp.from(now.plus(Duration.ofMinutes(30))), Timestamp.from(now.plus(Duration.ofDays(7))),
                AUTHORIZATION_ID);

        MvcResult result = mcp(ACCESS_TOKEN);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString()).contains("search_items");
    }

    /** As captured, the access token has expired: a clean 401, not a 500 and not a pass. */
    @Test
    void theExpiredLegacyAccessTokenIsRejectedWithA401() throws Exception {
        MvcResult result = mcp(ACCESS_TOKEN);

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("search_items");
    }

    /** The cleanup reads the connection start out of the legacy attributes and retires the row on age. */
    @Test
    void theCleanupJudgesTheLegacyRowByItsStoredConnectionStart() {
        // A one-day limit two days in: the refresh token (seven days) is still valid, so only the age can decide.
        Duration maxAge = Duration.ofDays(1);
        Instant start = Instant.ofEpochSecond(STARTED_AT);

        assertThat(authorizationService.deleteExpired(start.plus(Duration.ofMinutes(2)), maxAge)).isZero();
        assertThat(authorizationService.deleteExpired(start.plus(Duration.ofDays(2)), maxAge)).isEqualTo(1);
    }

    private MvcResult mcp(String bearerToken) throws Exception {
        return mockMvc.perform(post(McpEndpoints.MCP)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Accept", ACCEPT)
                        .content(toolsListRequest()))
                .andReturn();
    }
}
