package com.boxcopilot.backend.mcp.web;

import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.mcp.authserver.McpAuthorizationServerEndpoints;
import com.boxcopilot.backend.mcp.authserver.McpSessionUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * Serves the consent page of an authorization that is waiting for the user's decision.
 *
 * <p>Nothing on the page is taken from the query string except the {@code state}, which is used to look the
 * in-flight authorization up. Client id, requested scopes and redirect URI all come from that stored
 * authorization, so a hand-crafted consent URL cannot make the page show a different client or a different
 * redirect target. The lookup runs through the authorization decorator, which applies the access policy again.
 */
@Controller
@ConditionalOnProperty(prefix = "app.mcp", name = "enabled", havingValue = "true")
class McpConsentController {

    private static final Logger log = LoggerFactory.getLogger(McpConsentController.class);

    private static final OAuth2TokenType STATE_TOKEN_TYPE = new OAuth2TokenType(OAuth2ParameterNames.STATE);

    private static final String EXPIRED_PAGE = """
            <!DOCTYPE html><html lang="de"><head><meta charset="utf-8"><title>Abgelaufen</title></head>
            <body><h1>Abgelaufen</h1>
            <p>Diese Anfrage ist nicht mehr gültig. Bitte starten Sie die Verbindung in Claude erneut.</p>
            </body></html>""";

    private final OAuth2AuthorizationService authorizationService;
    private final McpSessionUserResolver userResolver;

    McpConsentController(OAuth2AuthorizationService authorizationService, McpSessionUserResolver userResolver) {
        this.authorizationService = authorizationService;
        this.userResolver = userResolver;
    }

    @GetMapping(McpAuthorizationServerEndpoints.CONSENT)
    void consent(@RequestParam(OAuth2ParameterNames.STATE) String state, Authentication authentication,
                 HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> user = this.userResolver.resolve(authentication);
        Optional<OAuth2Authorization> authorization = pendingAuthorization(state, user);
        if (user.isEmpty() || authorization.isEmpty()) {
            log.debug("MCP consent page rejected: reason=NO_PENDING_AUTHORIZATION");
            McpSecureHtmlResponse.write(response, HttpStatus.FORBIDDEN.value(), EXPIRED_PAGE);
            return;
        }
        OAuth2AuthorizationRequest authorizationRequest =
                authorization.get().getAttribute(OAuth2AuthorizationRequest.class.getName());
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        McpConsentPage.View view = new McpConsentPage.View(user.get().getUsername(),
                redirectHost(authorizationRequest.getRedirectUri()), List.copyOf(authorizationRequest.getScopes()),
                authorizationRequest.getClientId(), state, csrfToken.getParameterName(), csrfToken.getToken());
        McpSecureHtmlResponse.write(response, HttpStatus.OK.value(), McpConsentPage.render(view));
    }

    private Optional<OAuth2Authorization> pendingAuthorization(String state, Optional<User> user) {
        if (user.isEmpty()) {
            return Optional.empty();
        }
        OAuth2Authorization authorization = this.authorizationService.findByToken(state, STATE_TOKEN_TYPE);
        if (authorization == null
                || !String.valueOf(user.get().getId()).equals(authorization.getPrincipalName())
                || authorization.getAttribute(OAuth2AuthorizationRequest.class.getName()) == null) {
            return Optional.empty();
        }
        return Optional.of(authorization);
    }

    private static String redirectHost(String redirectUri) {
        try {
            String host = URI.create(redirectUri).getHost();
            return host == null ? redirectUri : host;
        } catch (IllegalArgumentException e) {
            return redirectUri;
        }
    }
}
