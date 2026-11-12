package com.boxcopilot.backend.mcp.security;

import com.boxcopilot.backend.domain.AuthProvider;
import com.boxcopilot.backend.domain.Role;
import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.mcp.McpAccessDeniedException;
import com.boxcopilot.backend.mcp.McpProperties;
import com.boxcopilot.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Every deny reason of the fail-closed policy, including "something went wrong". */
class McpAccessPolicyTest {

    private static final long USER_ID = 42L;
    private static final String CLIENT_ID = "registered-client-id";
    private static final Duration MAX_AGE = Duration.ofDays(14);
    private static final Instant NOW = Instant.parse("2026-09-22T12:00:00Z");

    private final UserRepository userRepository = mock(UserRepository.class);
    private final RegisteredClientRepository clientRepository = mock(RegisteredClientRepository.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final McpAccessPolicy policy = new McpAccessPolicy(userRepository, clientRepository,
            new McpProperties(true, "https://boxpilot.example.com", List.of("https://claude.ai/cb"), MAX_AGE,
                    null, null, null),
            clock);

    private static OAuth2Authorization startedAt(Instant startedAt) {
        return authorizationBuilder()
                .attribute(McpOAuth2Attributes.STARTED_AT, String.valueOf(startedAt.getEpochSecond()))
                .build();
    }

    private static User user() {
        User user = new User("someone", "Someone", AuthProvider.LOCAL, Role.USER);
        user.setId(USER_ID);
        user.setEnabled(true);
        user.setMcpAllowed(true);
        return user;
    }

    private static RegisteredClient client(String boundUserId) {
        ClientSettings.Builder settings = ClientSettings.builder();
        if (boundUserId != null) {
            settings.setting(McpOAuth2Attributes.CLIENT_USER_ID, boundUserId);
        }
        return RegisteredClient.withId(CLIENT_ID)
                .clientId("opaque-client-id")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://claude.ai/cb")
                .scope(McpOAuth2Attributes.SCOPE_READ)
                .clientSettings(settings.build())
                .build();
    }

    private static OAuth2Authorization.Builder authorizationBuilder() {
        return OAuth2Authorization.withRegisteredClient(client(String.valueOf(USER_ID)))
                .id("authorization-id")
                .principalName(String.valueOf(USER_ID))
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizedScopes(java.util.Set.of(McpOAuth2Attributes.SCOPE_READ));
    }

    private static OAuth2Authorization authorization() {
        return startedAt(NOW);
    }

    @BeforeEach
    void bindHappyPath() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(clientRepository.findById(CLIENT_ID)).thenReturn(client(String.valueOf(USER_ID)));
    }

    @Test
    void allowsAnActiveUserOnAFreshlyStartedConnection() {
        assertThat(policy.checkAuthorization(authorization())).isTrue();
        assertThatCode(() -> policy.requireActiveUser(USER_ID)).doesNotThrowAnyException();
    }

    @Test
    void deniesUnknownUser() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThat(policy.checkAuthorization(authorization())).isFalse();
        assertThatThrownBy(() -> policy.requireActiveUser(USER_ID)).isInstanceOf(McpAccessDeniedException.class);
    }

    @Test
    void deniesDisabledUser() {
        User disabled = user();
        disabled.setEnabled(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(disabled));

        assertThat(policy.checkAuthorization(authorization())).isFalse();
        assertThatThrownBy(() -> policy.requireActiveUser(USER_ID)).isInstanceOf(McpAccessDeniedException.class);
    }

    @Test
    void deniesLockedUser() {
        User locked = user();
        locked.setLockedUntil(LocalDateTime.now().plusHours(1));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(locked));

        assertThat(policy.checkAuthorization(authorization())).isFalse();
        assertThatThrownBy(() -> policy.requireActiveUser(USER_ID)).isInstanceOf(McpAccessDeniedException.class);
    }

    /**
     * Ending one's own connections only ever takes access away, so a lock - which is exactly when a user wants
     * to cut a connection - does not stand in the way. Everything else still denies.
     */
    @Test
    void letsALockedUserGiveUpTheirOwnAccess() {
        User locked = user();
        locked.setLockedUntil(LocalDateTime.now().plusHours(1));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(locked));

        assertThatCode(() -> policy.requireUserAllowedToRevoke(USER_ID)).doesNotThrowAnyException();
    }

    @Test
    void refusesEvenARevocationForAUserThatIsNotAllowedAtAll() {
        User disabled = user();
        disabled.setEnabled(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> policy.requireUserAllowedToRevoke(USER_ID))
                .isInstanceOf(McpAccessDeniedException.class);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> policy.requireUserAllowedToRevoke(USER_ID))
                .isInstanceOf(McpAccessDeniedException.class);
    }

    @Test
    void allowsUserWhoseLockHasExpired() {
        User wasLocked = user();
        wasLocked.setLockedUntil(LocalDateTime.now().minusMinutes(1));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(wasLocked));

        assertThat(policy.checkAuthorization(authorization())).isTrue();
    }

    @Test
    void deniesUserWithoutMcpPermission() {
        User notAllowed = user();
        notAllowed.setMcpAllowed(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(notAllowed));

        assertThat(policy.checkAuthorization(authorization())).isFalse();
        assertThatThrownBy(() -> policy.requireActiveUser(USER_ID)).isInstanceOf(McpAccessDeniedException.class);
    }

    @Test
    void deniesWhenTheClientIsBoundToAnotherUser() {
        when(clientRepository.findById(CLIENT_ID)).thenReturn(client("99"));

        assertThat(policy.checkAuthorization(authorization())).isFalse();
    }

    @Test
    void deniesWhenTheClientHasNoBinding() {
        when(clientRepository.findById(CLIENT_ID)).thenReturn(client(null));

        assertThat(policy.checkAuthorization(authorization())).isFalse();
    }

    @Test
    void deniesWhenTheClientNoLongerExists() {
        when(clientRepository.findById(CLIENT_ID)).thenReturn(null);

        assertThat(policy.checkAuthorization(authorization())).isFalse();
    }

    @Test
    void deniesWhenThePrincipalIsNotANumericUserId() {
        OAuth2Authorization authorization = authorizationBuilder()
                .principalName("someone")
                .attribute(McpOAuth2Attributes.STARTED_AT, String.valueOf(NOW.getEpochSecond()))
                .build();

        assertThat(policy.checkAuthorization(authorization)).isFalse();
    }

    @Test
    void deniesWhenTheStartTimestampIsMissing() {
        assertThat(policy.checkAuthorization(authorizationBuilder().build())).isFalse();
    }

    @Test
    void deniesWhenTheStartTimestampIsNotANumber() {
        OAuth2Authorization authorization = authorizationBuilder()
                .attribute(McpOAuth2Attributes.STARTED_AT, "yesterday")
                .build();

        assertThat(policy.checkAuthorization(authorization)).isFalse();
    }

    @Test
    void deniesWhenTheStartTimestampIsNotAString() {
        OAuth2Authorization authorization = authorizationBuilder()
                .attribute(McpOAuth2Attributes.STARTED_AT, NOW.getEpochSecond())
                .build();

        assertThat(policy.checkAuthorization(authorization)).isFalse();
    }

    @Test
    void deniesWhenTheStartTimestampLiesInTheFuture() {
        Instant beyondTolerance = NOW.plus(McpAuthorizationPolicy.MAX_CLOCK_SKEW).plusSeconds(1);

        assertThat(policy.checkAuthorization(startedAt(beyondTolerance))).isFalse();
        assertThat(policy.checkAuthorization(startedAt(NOW.plus(Duration.ofDays(365))))).isFalse();
    }

    @Test
    void toleratesASmallClockDifference() {
        assertThat(policy.checkAuthorization(startedAt(NOW.plus(McpAuthorizationPolicy.MAX_CLOCK_SKEW)))).isTrue();
    }

    @Test
    void deniesOneSecondAfterTheAbsoluteConnectionAgeIsExceeded() {
        assertThat(policy.checkAuthorization(startedAt(NOW.minus(MAX_AGE).minusSeconds(1)))).isFalse();
    }

    @Test
    void allowsExactlyOnTheAbsoluteConnectionAgeBoundary() {
        assertThat(policy.checkAuthorization(startedAt(NOW.minus(MAX_AGE)))).isTrue();
    }

    @Test
    void deniesWhenThereIsNoAuthorization() {
        assertThat(policy.checkAuthorization(null)).isFalse();
    }

    @Test
    void deniesWhenTheUserLookupFails() {
        when(userRepository.findById(anyLong())).thenThrow(new IllegalStateException("database is down"));

        assertThat(policy.checkAuthorization(authorization())).isFalse();
        assertThatThrownBy(() -> policy.requireActiveUser(USER_ID)).isInstanceOf(McpAccessDeniedException.class);
    }

    @Test
    void deniesWhenTheClientLookupFails() {
        when(clientRepository.findById(anyString())).thenThrow(new IllegalStateException("database is down"));

        assertThat(policy.checkAuthorization(authorization())).isFalse();
    }
}
