package com.boxcopilot.backend.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Answers the SPA's logout XHR with 200 {@code {"redirectUrl": ...}} instead of a redirect, so the browser never
 * follows the identity provider cross-origin inside the XHR. The SPA then navigates to {@code redirectUrl}: the
 * IdP end-session URL for users who signed in via OIDC, the frontend itself for everyone else. Both values come
 * from server configuration only.
 */
final class OidcLogoutSuccessHandler implements LogoutSuccessHandler {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final String frontendUrl;
    private final String idpLogoutUrl;

    OidcLogoutSuccessHandler(String frontendUrl, String idpLogoutUrl) {
        this.frontendUrl = frontendUrl;
        this.idpLogoutUrl = idpLogoutUrl;
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
                                Authentication authentication) throws IOException {
        String target = authentication instanceof OAuth2AuthenticationToken
                ? UriComponentsBuilder.fromUriString(idpLogoutUrl).queryParam("redirect_uri", frontendUrl)
                        .build().encode().toUriString()
                : frontendUrl;
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(JSON.writeValueAsString(Map.of("redirectUrl", target)));
    }
}
