package com.boxcopilot.backend.mcp.authserver;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationConverter;

import java.util.HashMap;
import java.util.Map;

/**
 * Extracts the client id of a public client from a refresh-token or revocation request.
 *
 * @see McpPublicClientRequests for why these two requests need a converter of their own
 */
final class McpPublicClientAuthenticationConverter implements AuthenticationConverter {

    private final String revocationEndpoint;

    McpPublicClientAuthenticationConverter(String revocationEndpoint) {
        this.revocationEndpoint = revocationEndpoint;
    }

    @Override
    public Authentication convert(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return null;
        }
        McpPublicClientRequests decision = new McpPublicClientRequests(
                request.getParameter(OAuth2ParameterNames.GRANT_TYPE),
                request.getParameter(PkceParameterNames.CODE_VERIFIER) != null,
                request.getParameter(OAuth2ParameterNames.REFRESH_TOKEN),
                request.getParameter(OAuth2ParameterNames.TOKEN),
                this.revocationEndpoint.equals(request.getRequestURI()));
        if (!decision.isAuthenticatedByClientId()) {
            return null;
        }
        if (request.getParameter(OAuth2ParameterNames.CLIENT_SECRET) != null) {
            // A public client has no secret; sending one means the client is misconfigured, not authenticated.
            return null;
        }
        String[] clientIds = request.getParameterValues(OAuth2ParameterNames.CLIENT_ID);
        if (clientIds == null || clientIds.length != 1 || clientIds[0].isBlank()) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
        }
        return new OAuth2ClientAuthenticationToken(clientIds[0], ClientAuthenticationMethod.NONE, null,
                additionalParameters(request));
    }

    private static Map<String, Object> additionalParameters(HttpServletRequest request) {
        Map<String, Object> parameters = new HashMap<>();
        request.getParameterMap().forEach((name, values) -> {
            if (!OAuth2ParameterNames.CLIENT_ID.equals(name)) {
                parameters.put(name, values.length == 1 ? values[0] : values);
            }
        });
        return parameters;
    }
}
