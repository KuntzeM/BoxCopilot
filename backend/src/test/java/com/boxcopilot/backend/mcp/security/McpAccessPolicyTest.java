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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
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

    private final UserRepository userRepository = mock(UserRepository.class);
    private final RegisteredClientRepository clientRepository = mock(RegisteredClientRepository.class);
    private final McpAccessPolicy policy = new McpAccessPolicy(userRepository, clientRepository,
            new McpProperties(true, "https://boxpilot.example.com", List.of("https://claude.ai/cb"), MAX_AGE));

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
        return authorizationBuilder()
                .attribute(McpOAuth2Attributes.STARTED_AT, String.valueOf(Instant.now().getEpochSecond()))
                .build();
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
                .attribute(McpOAuth2Attributes.STARTED_AT, String.valueOf(Instant.now().getEpochSecond()))
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
                .attribute(McpOAuth2Attributes.STARTED_AT, Instant.now().getEpochSecond())
                .build();

        assertThat(policy.checkAuthorization(authorization)).isFalse();
    }

    @Test
    void deniesWhenTheAbsoluteConnectionAgeIsExceeded() {
        OAuth2Authorization tooOld = authorizationBuilder()
                .attribute(McpOAuth2Attributes.STARTED_AT,
                        String.valueOf(Instant.now().minus(MAX_AGE).minusSeconds(60).getEpochSecond()))
                .build();

        assertThat(policy.checkAuthorization(tooOld)).isFalse();
    }

    @Test
    void allowsJustBeforeTheAbsoluteConnectionAgeIsExceeded() {
        OAuth2Authorization almostTooOld = authorizationBuilder()
                .attribute(McpOAuth2Attributes.STARTED_AT,
                        String.valueOf(Instant.now().minus(MAX_AGE).plusSeconds(60).getEpochSecond()))
                .build();

        assertThat(policy.checkAuthorization(almostTooOld)).isTrue();
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
