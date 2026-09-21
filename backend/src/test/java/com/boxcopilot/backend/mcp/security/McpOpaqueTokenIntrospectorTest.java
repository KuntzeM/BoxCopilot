package com.boxcopilot.backend.mcp.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The bearer token side: only an active access token of an allowed authorization authenticates. */
class McpOpaqueTokenIntrospectorTest {

    private static final RegisteredClient CLIENT = RegisteredClient.withId("client")
            .clientId("opaque-client-id")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://claude.ai/cb")
            .scope(McpOAuth2Attributes.SCOPE_READ)
            .clientSettings(ClientSettings.builder().setting(McpOAuth2Attributes.CLIENT_USER_ID, "42").build())
            .build();

    private final OAuth2AuthorizationService authorizationService = mock(OAuth2AuthorizationService.class);
    private final McpOpaqueTokenIntrospector introspector = new McpOpaqueTokenIntrospector(authorizationService);

    private static OAuth2Authorization authorization(Instant issuedAt, Instant expiresAt, Set<String> scopes) {
        return OAuth2Authorization.withRegisteredClient(CLIENT)
                .id("authorization-id")
                .principalName("42")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizedScopes(scopes)
                .token(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access-value", issuedAt,
                        expiresAt, scopes))
                .refreshToken(new OAuth2RefreshToken("refresh-value", issuedAt))
                .build();
    }

    private static OAuth2Authorization active() {
        Instant now = Instant.now();
        return authorization(now.minusSeconds(60), now.plus(Duration.ofMinutes(30)),
                Set.of(McpOAuth2Attributes.SCOPE_READ));
    }

    @Test
    void anActiveAccessTokenYieldsTheUserIdAndTheReadScope() {
        OAuth2Authorization authorization = active();
        when(authorizationService.findByToken("access-value", OAuth2TokenType.ACCESS_TOKEN))
                .thenReturn(authorization);

        OAuth2AuthenticatedPrincipal principal = introspector.introspect("access-value");

        assertThat(principal.getName()).isEqualTo("42");
        assertThat(principal.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("SCOPE_mcp:read");
        assertThat(principal.getAttributes()).containsOnlyKeys("sub", "scope", "iat", "exp");
        assertThat(principal.<String>getAttribute("sub")).isEqualTo("42");
        assertThat(principal.<String>getAttribute("scope")).isEqualTo("mcp:read");
        assertThat(principal.<Instant>getAttribute("exp")).isAfter(Instant.now());
    }

    @Test
    void theLookupIsTypedToAccessTokensSoARefreshTokenIsNeverFound() {
        when(authorizationService.findByToken(anyString(), any())).thenReturn(null);

        assertThatThrownBy(() -> introspector.introspect("refresh-value"))
                .isInstanceOf(BadOpaqueTokenException.class);

        verify(authorizationService).findByToken("refresh-value", OAuth2TokenType.ACCESS_TOKEN);
    }

    @Test
    void anAuthorizationHiddenByThePolicyIsRejected() {
        when(authorizationService.findByToken(anyString(), any())).thenReturn(null);

        assertThatThrownBy(() -> introspector.introspect("access-value"))
                .isInstanceOf(BadOpaqueTokenException.class);
    }

    @Test
    void anExpiredAccessTokenIsRejected() {
        Instant now = Instant.now();
        OAuth2Authorization expired = authorization(now.minus(Duration.ofHours(2)), now.minus(Duration.ofHours(1)),
                Set.of(McpOAuth2Attributes.SCOPE_READ));
        when(authorizationService.findByToken(anyString(), any())).thenReturn(expired);

        assertThatThrownBy(() -> introspector.introspect("access-value"))
                .isInstanceOf(BadOpaqueTokenException.class);
    }

    @Test
    void anAuthorizationWithoutAccessTokenIsRejected() {
        OAuth2Authorization withoutToken = OAuth2Authorization.withRegisteredClient(CLIENT)
                .id("authorization-id")
                .principalName("42")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .build();
        when(authorizationService.findByToken(anyString(), any())).thenReturn(withoutToken);

        assertThatThrownBy(() -> introspector.introspect("access-value"))
                .isInstanceOf(BadOpaqueTokenException.class);
    }

    @Test
    void anAuthorityIsOnlyDerivedFromAScopeThatBothTheTokenAndTheAuthorizationCarry() {
        Instant now = Instant.now();
        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(CLIENT)
                .id("authorization-id")
                .principalName("42")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizedScopes(Set.of(McpOAuth2Attributes.SCOPE_READ, "mcp:admin"))
                .token(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access-value", now,
                        now.plus(Duration.ofMinutes(30)), Set.of(McpOAuth2Attributes.SCOPE_READ, "mcp:write")))
                .build();
        when(authorizationService.findByToken(anyString(), any())).thenReturn(authorization);

        OAuth2AuthenticatedPrincipal principal = introspector.introspect("access-value");

        assertThat(principal.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("SCOPE_mcp:read");
        assertThat(principal.<String>getAttribute("scope")).isEqualTo("mcp:read");
    }

    @Test
    void noAuthorityIsDerivedFromAnythingButTheAuthorizedScopes() {
        Instant now = Instant.now();
        when(authorizationService.findByToken(anyString(), any()))
                .thenReturn(authorization(now, now.plus(Duration.ofMinutes(30)), Set.of()));

        assertThat(introspector.introspect("access-value").getAuthorities()).isEmpty();
    }
}
