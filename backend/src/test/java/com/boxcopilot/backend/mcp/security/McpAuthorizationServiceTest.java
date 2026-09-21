package com.boxcopilot.backend.mcp.security;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The decorator: it stamps the connection start once, and hides everything the policy rejects. */
class McpAuthorizationServiceTest {

    private static final RegisteredClient CLIENT = RegisteredClient.withId("client")
            .clientId("opaque-client-id")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .redirectUri("https://claude.ai/cb")
            .scope(McpOAuth2Attributes.SCOPE_READ)
            .clientSettings(ClientSettings.builder().setting(McpOAuth2Attributes.CLIENT_USER_ID, "42").build())
            .build();

    private final OAuth2AuthorizationService delegate = mock(OAuth2AuthorizationService.class);
    private final McpAccessPolicy policy = mock(McpAccessPolicy.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final McpAuthorizationService service = new McpAuthorizationService(delegate, policy, jdbcTemplate);

    private static OAuth2Authorization.Builder builder() {
        return OAuth2Authorization.withRegisteredClient(CLIENT)
                .id("authorization-id")
                .principalName("42")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizedScopes(Set.of(McpOAuth2Attributes.SCOPE_READ));
    }

    private static OAuth2Authorization withRefreshToken(OAuth2Authorization.Builder builder, String value,
                                                        Instant issuedAt) {
        return builder.token(new OAuth2RefreshToken(value, issuedAt, issuedAt.plus(Duration.ofDays(7)))).build();
    }

    private OAuth2Authorization captureSaved() {
        ArgumentCaptor<OAuth2Authorization> captor = ArgumentCaptor.forClass(OAuth2Authorization.class);
        verify(delegate).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void theFirstSaveStampsTheConnectionStart() {
        Instant before = Instant.now();

        service.save(builder().build());

        String startedAt = captureSaved().getAttribute(McpOAuth2Attributes.STARTED_AT);
        assertThat(Instant.ofEpochSecond(Long.parseLong(startedAt)))
                .isBetween(before.minusSeconds(5), Instant.now().plusSeconds(5));
    }

    @Test
    void aLaterSaveLeavesTheConnectionStartUntouched() {
        String startedAt = String.valueOf(Instant.now().minus(Duration.ofDays(3)).getEpochSecond());

        service.save(builder().attribute(McpOAuth2Attributes.STARTED_AT, startedAt).build());

        assertThat(captureSaved().<String>getAttribute(McpOAuth2Attributes.STARTED_AT)).isEqualTo(startedAt);
    }

    @Test
    void aRefreshTokenRotationKeepsTheConnectionStartAndGetsAFreshTokenLifetime() {
        Instant firstIssuedAt = Instant.now().minus(Duration.ofDays(3));
        OAuth2Authorization first = withRefreshToken(builder(), "refresh-1", firstIssuedAt);
        service.save(first);
        OAuth2Authorization stamped = captureSaved();

        // This is what Spring Authorization Server does on a refresh: copy, then replace the refresh token.
        Instant rotatedAt = Instant.now();
        OAuth2Authorization rotated = withRefreshToken(OAuth2Authorization.from(stamped), "refresh-2", rotatedAt);
        service.save(rotated);

        ArgumentCaptor<OAuth2Authorization> captor = ArgumentCaptor.forClass(OAuth2Authorization.class);
        verify(delegate, org.mockito.Mockito.times(2)).save(captor.capture());
        OAuth2Authorization saved = captor.getAllValues().get(1);
        assertThat(saved.<String>getAttribute(McpOAuth2Attributes.STARTED_AT))
                .isEqualTo(stamped.<String>getAttribute(McpOAuth2Attributes.STARTED_AT));
        assertThat(saved.getRefreshToken()).isNotNull();
        assertThat(saved.getRefreshToken().getToken().getTokenValue()).isEqualTo("refresh-2");
        assertThat(saved.getRefreshToken().getToken().getExpiresAt())
                .isAfter(first.getRefreshToken().getToken().getExpiresAt());
    }

    @Test
    void findByIdReturnsTheAuthorizationWhenThePolicyAllows() {
        OAuth2Authorization authorization = builder().build();
        when(delegate.findById("authorization-id")).thenReturn(authorization);
        when(policy.checkAuthorization(authorization)).thenReturn(true);

        assertThat(service.findById("authorization-id")).isSameAs(authorization);
    }

    @Test
    void findByIdReturnsNullWhenThePolicyDenies() {
        OAuth2Authorization authorization = builder().build();
        when(delegate.findById("authorization-id")).thenReturn(authorization);
        when(policy.checkAuthorization(authorization)).thenReturn(false);

        assertThat(service.findById("authorization-id")).isNull();
    }

    @Test
    void findByIdReturnsNullWhenNothingIsStored() {
        when(delegate.findById("authorization-id")).thenReturn(null);

        assertThat(service.findById("authorization-id")).isNull();
        verifyNoInteractions(policy);
    }

    @Test
    void findByTokenAppliesThePolicyForEveryTokenType() {
        OAuth2Authorization authorization = builder().build();
        when(delegate.findByToken(anyString(), any())).thenReturn(authorization);
        when(policy.checkAuthorization(authorization)).thenReturn(false);

        assertThat(service.findByToken("value", OAuth2TokenType.ACCESS_TOKEN)).isNull();
        assertThat(service.findByToken("value", OAuth2TokenType.REFRESH_TOKEN)).isNull();
        assertThat(service.findByToken("value", new OAuth2TokenType(OAuth2AccessToken.TokenType.BEARER.getValue())))
                .isNull();
        assertThat(service.findByToken("value", null)).isNull();

        when(policy.checkAuthorization(authorization)).thenReturn(true);
        assertThat(service.findByToken("value", OAuth2TokenType.ACCESS_TOKEN)).isSameAs(authorization);
    }

    @Test
    void removePassesThrough() {
        OAuth2Authorization authorization = builder().build();

        service.remove(authorization);

        verify(delegate).remove(authorization);
        verifyNoInteractions(policy);
    }

    @Test
    void removeByPrincipalDeletesEveryAuthorizationOfThatUser() {
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(3);

        assertThat(service.removeByPrincipal("42")).isEqualTo(3);

        verify(jdbcTemplate).update("DELETE FROM oauth2_authorization WHERE principal_name = ?", "42");
    }
}
