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
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.HashMap;
import java.util.Map;

/**
 * Extracts the client id of a public client from a refresh-token or revocation request.
 *
 * @see McpPublicClientRequests for why these two requests need a converter of their own
 */
final class McpPublicClientAuthenticationConverter implements AuthenticationConverter {

    private final RequestMatcher revocationEndpoint;

    /**
     * @param revocationEndpoint matcher for the revocation endpoint; a matcher rather than a path comparison, so
     *                           that a servlet context path cannot silently break the decision
     */
    McpPublicClientAuthenticationConverter(RequestMatcher revocationEndpoint) {
        this.revocationEndpoint = revocationEndpoint;
    }

    @Override
    public Authentication convert(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return null;
        }
        boolean revocation = this.revocationEndpoint.matches(request);
        McpPublicClientRequests decision = new McpPublicClientRequests(
                request.getParameter(OAuth2ParameterNames.GRANT_TYPE),
                request.getParameter(PkceParameterNames.CODE_VERIFIER) != null,
                request.getParameter(OAuth2ParameterNames.REFRESH_TOKEN),
                request.getParameter(OAuth2ParameterNames.TOKEN),
                revocation);
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
                additionalParameters(request, revocation));
    }

    /**
     * The form parameters minus the client id, plus the endpoint marker the provider re-reads. Any incoming
     * parameter of the marker's name is dropped first, so the marker always states what the server observed and
     * never what a client sent.
     */
    private static Map<String, Object> additionalParameters(HttpServletRequest request, boolean revocation) {
        Map<String, Object> parameters = new HashMap<>();
        request.getParameterMap().forEach((name, values) -> {
            if (!OAuth2ParameterNames.CLIENT_ID.equals(name)
                    && !McpPublicClientRequests.REVOCATION_MARKER.equals(name)) {
                parameters.put(name, values.length == 1 ? values[0] : values);
            }
        });
        parameters.put(McpPublicClientRequests.REVOCATION_MARKER, revocation);
        return parameters;
    }
}
