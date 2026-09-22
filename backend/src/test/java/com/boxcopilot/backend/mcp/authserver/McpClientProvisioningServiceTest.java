package com.boxcopilot.backend.mcp.authserver;

import com.boxcopilot.backend.mcp.McpAccessDeniedException;
import com.boxcopilot.backend.mcp.McpProperties;
import com.boxcopilot.backend.mcp.McpUserGate;
import com.boxcopilot.backend.mcp.security.McpAuthorizationRegistry;
import com.boxcopilot.backend.mcp.security.McpOAuth2Attributes;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The provisioning rules: lazy, idempotent, gated, and never touching another user's client. */
class McpClientProvisioningServiceTest {

    private static final long USER_ID = 42L;
    private static final String PRINCIPAL = "42";
    private static final List<String> REDIRECT_URIS = List.of("https://claude.ai/api/mcp/auth_callback");

    private final McpUserClients userClients = mock(McpUserClients.class);
    private final RegisteredClientRepository registeredClientRepository = mock(RegisteredClientRepository.class);
    private final McpAuthorizationRegistry authorizations = mock(McpAuthorizationRegistry.class);
    private final McpUserGate userGate = mock(McpUserGate.class);
    private final McpUserLock userLock = mock(McpUserLock.class);
    private final McpClientIdGenerator clientIdGenerator = mock(McpClientIdGenerator.class);

    private final McpClientProvisioningService service = new McpClientProvisioningService(userClients,
            registeredClientRepository, authorizations, userGate, userLock, clientIdGenerator,
            new McpProperties(true, "https://boxpilot.example", REDIRECT_URIS, Duration.ofDays(14), null));

    private static RegisteredClient existingClient(String clientId) {
        return RegisteredClient.withId("row-id")
                .clientId(clientId)
                .clientName("Claude connection")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(REDIRECT_URIS.get(0))
                .scope(McpOAuth2Attributes.SCOPE_READ)
                .clientSettings(McpRegisteredClients.clientSettings(USER_ID))
                .build();
    }

    // ---------------------------------------------------------------- provision

    @Test
    void provisionCreatesTheClientOfAUserThatHasNoneYet() {
        when(userClients.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(clientIdGenerator.newClientId()).thenReturn("generated-client-id");

        String clientId = service.provision(USER_ID);

        assertThat(clientId).isEqualTo("generated-client-id");
        ArgumentCaptor<RegisteredClient> saved = ArgumentCaptor.forClass(RegisteredClient.class);
        verify(registeredClientRepository).save(saved.capture());
        RegisteredClient client = saved.getValue();
        assertThat(client.getClientId()).isEqualTo("generated-client-id");
        assertThat(client.getClientSettings().<String>getSetting(McpOAuth2Attributes.CLIENT_USER_ID))
                .isEqualTo(PRINCIPAL);
        assertThat(client.getRedirectUris()).containsExactlyElementsOf(REDIRECT_URIS);
        assertThat(client.getClientAuthenticationMethods()).containsExactly(ClientAuthenticationMethod.NONE);
        assertThat(client.getAuthorizationGrantTypes()).containsExactlyInAnyOrder(
                AuthorizationGrantType.AUTHORIZATION_CODE, AuthorizationGrantType.REFRESH_TOKEN);
        assertThat(client.getScopes()).containsExactly(McpOAuth2Attributes.SCOPE_READ);
        assertThat(client.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(client.getTokenSettings().getAccessTokenTimeToLive())
                .isEqualTo(McpRegisteredClients.ACCESS_TOKEN_TIME_TO_LIVE);
    }

    @Test
    void provisionReturnsTheExistingClientUntouched() {
        when(userClients.findByUserId(USER_ID)).thenReturn(Optional.of(existingClient("existing-client-id")));

        assertThat(service.provision(USER_ID)).isEqualTo("existing-client-id");

        verify(registeredClientRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(userClients, never()).deleteByUserId(anyLong());
        verifyNoInteractions(authorizations, clientIdGenerator);
    }

    @Test
    void provisionIsRefusedForAUserTheGateRejects() {
        doThrow(new McpAccessDeniedException("denied")).when(userGate).requireActiveUser(USER_ID);

        assertThatThrownBy(() -> service.provision(USER_ID)).isInstanceOf(McpAccessDeniedException.class);

        verifyNoInteractions(userClients, registeredClientRepository, authorizations, clientIdGenerator);
    }

    /**
     * The lock has to be held before the state of the user is read, or a concurrent request could pass the same
     * check and create a second client.
     */
    @Test
    void provisionTakesTheUserLockBeforeItReadsAnything() {
        when(userClients.findByUserId(USER_ID)).thenReturn(Optional.of(existingClient("existing-client-id")));

        service.provision(USER_ID);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(userLock, userGate, userClients);
        order.verify(userLock).lock(USER_ID);
        order.verify(userGate).requireActiveUser(USER_ID);
        order.verify(userClients).findByUserId(USER_ID);
    }

    // ---------------------------------------------------------------- regenerate

    @Test
    void regenerateRemovesTheOldClientAndEveryConnectionBeforeCreatingTheNewOne() {
        when(clientIdGenerator.newClientId()).thenReturn("new-client-id");

        assertThat(service.regenerate(USER_ID)).isEqualTo("new-client-id");

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(userLock, authorizations, userClients,
                registeredClientRepository);
        order.verify(userLock).lock(USER_ID);
        order.verify(authorizations).removeByPrincipal(PRINCIPAL);
        order.verify(userClients).deleteByUserId(USER_ID);
        order.verify(registeredClientRepository).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void regenerateIsRefusedForAUserTheGateRejects() {
        doThrow(new McpAccessDeniedException("denied")).when(userGate).requireActiveUser(USER_ID);

        assertThatThrownBy(() -> service.regenerate(USER_ID)).isInstanceOf(McpAccessDeniedException.class);

        verifyNoInteractions(userClients, registeredClientRepository, authorizations, clientIdGenerator);
    }

    // ---------------------------------------------------------------- revoke

    /**
     * Giving up one's own connections asks the lenient gate: a temporary lock must not keep a user from ending
     * a connection, which is the one thing a locked account may still want to do.
     */
    @Test
    void revokeConnectionsDeletesTheAuthorizationsAndKeepsTheClient() {
        service.revokeConnections(USER_ID);

        verify(userGate).requireUserAllowedToRevoke(USER_ID);
        verify(userGate, never()).requireActiveUser(anyLong());
        verify(authorizations).removeByPrincipal(PRINCIPAL);
        verify(userClients, never()).deleteByUserId(anyLong());
        verify(registeredClientRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void revokeConnectionsIsRefusedForAUserTheGateRejects() {
        doThrow(new McpAccessDeniedException("denied")).when(userGate).requireUserAllowedToRevoke(USER_ID);

        assertThatThrownBy(() -> service.revokeConnections(USER_ID)).isInstanceOf(McpAccessDeniedException.class);

        verifyNoInteractions(authorizations, userClients);
    }

    /**
     * The teardown runs while access is being withdrawn, so the gate would refuse it; it must not be consulted.
     */
    @Test
    void revokeAllAccessDeletesClientAndConnectionsWithoutAskingTheGate() {
        when(authorizations.removeByPrincipal(PRINCIPAL)).thenReturn(2);
        when(userClients.deleteByUserId(USER_ID)).thenReturn(1);

        service.revokeAllAccess(USER_ID);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(userLock, authorizations, userClients);
        order.verify(userLock).lock(USER_ID);
        order.verify(authorizations).removeByPrincipal(PRINCIPAL);
        order.verify(userClients).deleteByUserId(USER_ID);
        verifyNoInteractions(userGate, registeredClientRepository);
    }
}
