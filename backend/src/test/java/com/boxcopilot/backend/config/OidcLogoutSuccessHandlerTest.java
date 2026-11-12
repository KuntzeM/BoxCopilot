package com.boxcopilot.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OidcLogoutSuccessHandlerTest {

    private final OidcLogoutSuccessHandler handler =
            new OidcLogoutSuccessHandler("https://box.example", "https://cloud.example/logout");

    private MockHttpServletResponse logout(org.springframework.security.core.Authentication auth) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onLogoutSuccess(new MockHttpServletRequest(), response, auth);
        return response;
    }

    @Test
    void oidcUserGetsTheIdpEndSessionUrlAsJsonNotARedirect() throws Exception {
        var user = new DefaultOAuth2User(AuthorityUtils.NO_AUTHORITIES, Map.of("sub", "1"), "sub");
        MockHttpServletResponse r = logout(new OAuth2AuthenticationToken(user, user.getAuthorities(), "nextcloud"));

        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(r.getRedirectedUrl()).isNull();
        assertThat(r.getContentType()).startsWith("application/json");
        assertThat(r.getContentAsString()).isEqualTo(
                "{\"redirectUrl\":\"https://cloud.example/logout?redirect_uri=https://box.example\"}");
    }

    @Test
    void localAndAnonymousUsersStayOnTheFrontend() throws Exception {
        for (var auth : new org.springframework.security.core.Authentication[] {
                UsernamePasswordAuthenticationToken.authenticated("alice", "x", AuthorityUtils.NO_AUTHORITIES), null}) {
            MockHttpServletResponse r = logout(auth);
            assertThat(r.getStatus()).isEqualTo(200);
            assertThat(r.getContentAsString()).isEqualTo("{\"redirectUrl\":\"https://box.example\"}");
        }
    }
}
