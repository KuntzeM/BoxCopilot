package com.boxcopilot.backend.service;

import com.boxcopilot.backend.domain.AuthProvider;
import com.boxcopilot.backend.domain.Role;
import com.boxcopilot.backend.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomOidcUserServiceTest {

    private final UserService userService = mock(UserService.class);
    private final CustomOidcUserService service = new CustomOidcUserService(userService);

    @Test
    void mapsDisabledUserToOAuth2AuthenticationException() {
        when(userService.createOrUpdateOidcUser("sub-1", "ncuser", "NC User", "nc@example.com"))
                .thenThrow(new CustomUserDetailsService.DisabledException("Account is disabled"));

        assertThatThrownBy(() -> service.toLocalUser(oidcUser()))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(ex -> assertThat(((OAuth2AuthenticationException) ex).getError().getErrorCode())
                        .isEqualTo("access_denied"));
    }

    @Test
    void addsRoleAuthorityForActiveUser() {
        User user = new User("ncuser", "NC User", AuthProvider.NEXTCLOUD, Role.ADMIN);
        when(userService.createOrUpdateOidcUser("sub-1", "ncuser", "NC User", "nc@example.com")).thenReturn(user);

        OidcUser result = service.toLocalUser(oidcUser());

        assertThat(result.getAuthorities()).extracting(Object::toString).contains("ROLE_ADMIN");
    }

    private static OidcUser oidcUser() {
        OidcIdToken idToken = new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(60), java.util.Map.of(
                "sub", "sub-1",
                "email", "nc@example.com",
                "name", "NC User",
                "preferred_username", "ncuser"));
        return new DefaultOidcUser(List.of(), idToken);
    }
}
