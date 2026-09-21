package com.boxcopilot.backend.mcp;

import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.mcp.security.McpOAuth2Attributes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Migration V14 and the JDBC store on the real database: a fully populated authorization with oversized
 * attributes has to survive a round trip, and Spring Authorization Server has to use our decorator.
 */
class McpAuthorizationStorePostgresTest extends McpIntegrationTestBase {

    @Autowired
    private ApplicationContext context;

    private static String longText(int length) {
        return "x".repeat(length);
    }

    @Test
    void migrationV14CreatedBothTablesWithTextColumns() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_name LIKE 'oauth2%'", String.class);
        assertThat(tables).containsExactlyInAnyOrder("oauth2_registered_client", "oauth2_authorization");

        List<String> textColumns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'oauth2_authorization' "
                        + "AND data_type = 'text'", String.class);
        assertThat(textColumns).contains("attributes", "access_token_value", "access_token_metadata",
                "refresh_token_value", "refresh_token_metadata", "authorization_code_value",
                "authorization_code_metadata");

        List<String> clientTextColumns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'oauth2_registered_client' "
                        + "AND data_type = 'text'", String.class);
        assertThat(clientTextColumns).contains("client_settings", "token_settings", "redirect_uris", "scopes");

        assertThat(jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_name = 'oauth2_authorization_consent'",
                String.class)).isEmpty();
    }

    @Test
    void aFullyPopulatedAuthorizationWithOversizedAttributesRoundTrips() {
        User user = createUser(true, true);
        RegisteredClient client = createClient(user.getId());
        String oversized = longText(6000);
        OAuth2Authorization saved = authorizationBuilder(client, user.getId(), "access-oversized",
                Instant.now().plus(Duration.ofMinutes(30)))
                .attribute(OAuth2ParameterNames.STATE, longText(400))
                .attribute("boxpilot.oversized", oversized)
                .build();

        authorizationService.save(saved);
        OAuth2Authorization loaded = authorizationService.findById(saved.getId());

        assertThat(loaded).isNotNull();
        assertThat(loaded.<String>getAttribute("boxpilot.oversized")).isEqualTo(oversized);
        assertThat(loaded.getAccessToken().getToken().getTokenValue()).isEqualTo("access-oversized");
        assertThat(loaded.getRefreshToken().getToken().getTokenValue()).isEqualTo("refresh-access-oversized");
        assertThat(loaded.getAuthorizedScopes()).containsExactly(McpOAuth2Attributes.SCOPE_READ);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT length(attributes) FROM oauth2_authorization WHERE id = ?", Integer.class, saved.getId()))
                .isGreaterThan(4000);
    }

    @Test
    void theStartTimestampSurvivesTheJacksonAllowlistAsAString() {
        User user = createUser(true, true);
        RegisteredClient client = createClient(user.getId());

        OAuth2Authorization saved = saveAuthorization(client, user.getId(), "access-started-at");
        OAuth2Authorization loaded = authorizationService.findById(saved.getId());

        assertThat(loaded).isNotNull();
        String startedAt = loaded.getAttribute(McpOAuth2Attributes.STARTED_AT);
        assertThat(Instant.ofEpochSecond(Long.parseLong(startedAt)))
                .isCloseTo(Instant.now(), org.assertj.core.api.Assertions.within(Duration.ofMinutes(1)));
    }

    @Test
    void theExchangedPrincipalRoundTripsThroughTheJacksonAllowlist() {
        User user = createUser(true, true);
        RegisteredClient client = createClient(user.getId());
        org.springframework.security.core.userdetails.User principal =
                new org.springframework.security.core.userdetails.User(String.valueOf(user.getId()), "", List.of());
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
        OAuth2Authorization saved = authorizationBuilder(client, user.getId(), "access-principal",
                Instant.now().plus(Duration.ofMinutes(30)))
                .attribute(Principal.class.getName(), authentication)
                .build();

        authorizationService.save(saved);
        OAuth2Authorization loaded = authorizationService.findById(saved.getId());

        assertThat(loaded).isNotNull();
        Authentication restored = loaded.getAttribute(Principal.class.getName());
        assertThat(restored).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(restored.getName()).isEqualTo(String.valueOf(user.getId()));
        assertThat(restored.getAuthorities()).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT attributes FROM oauth2_authorization WHERE id = ?", String.class, saved.getId()))
                .doesNotContain("passwordHash", "CustomUserPrincipal");
    }

    @Test
    void aRefreshRotationKeepsTheStartTimestampAndGivesTheNewTokenAFreshLifetime() {
        User user = createUser(true, true);
        RegisteredClient client = createClient(user.getId());
        OAuth2Authorization first = saveAuthorization(client, user.getId(), "access-rotation");
        OAuth2Authorization stored = authorizationService.findById(first.getId());
        Instant firstExpiry = stored.getRefreshToken().getToken().getExpiresAt();

        Instant rotatedAt = Instant.now();
        OAuth2Authorization rotated = OAuth2Authorization.from(stored)
                .token(new OAuth2RefreshToken("refresh-rotated", rotatedAt, rotatedAt.plus(Duration.ofDays(7))))
                .build();
        authorizationService.save(rotated);

        OAuth2Authorization loaded = authorizationService.findById(first.getId());
        assertThat(loaded.<String>getAttribute(McpOAuth2Attributes.STARTED_AT))
                .isEqualTo(stored.<String>getAttribute(McpOAuth2Attributes.STARTED_AT));
        assertThat(loaded.getRefreshToken().getToken().getTokenValue()).isEqualTo("refresh-rotated");
        assertThat(loaded.getRefreshToken().getToken().getExpiresAt()).isAfter(firstExpiry);
        assertThat(authorizationService.findByToken("refresh-access-rotation", OAuth2TokenType.REFRESH_TOKEN))
                .isNull();
    }

    @Test
    void theOnlyAuthorizationServiceInTheContextIsTheDecorator() {
        assertThat(context.getBeansOfType(OAuth2AuthorizationService.class)).hasSize(1);
        assertThat(context.getBean(OAuth2AuthorizationService.class)).isSameAs(authorizationService);
    }
}
