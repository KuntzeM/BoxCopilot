package com.boxcopilot.backend.mcp;

import io.modelcontextprotocol.common.McpTransportContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.web.servlet.function.ServerRequest;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Only a bearer token issued by this application may name the user a tool call runs as. */
class McpTransportContextExtractorTest {

    private final McpTransportContextExtractor extractor = new McpTransportContextExtractor();

    private static ServerRequest requestWith(Principal principal) {
        return requestWith(principal, null);
    }

    private static ServerRequest requestWith(Principal principal, String remoteAddress) {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", McpEndpoints.MCP);
        servletRequest.setUserPrincipal(principal);
        if (remoteAddress != null) {
            servletRequest.setRemoteAddr(remoteAddress);
        }
        return ServerRequest.create(servletRequest, List.of());
    }

    private static BearerTokenAuthentication bearerToken(String subject) {
        Instant now = Instant.now();
        var principal = new DefaultOAuth2AuthenticatedPrincipal(subject, Map.of("sub", subject),
                List.of(new SimpleGrantedAuthority("SCOPE_mcp:read")));
        var accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "token-value", now,
                now.plusSeconds(1800));
        return new BearerTokenAuthentication(principal, accessToken, principal.getAuthorities());
    }

    @Test
    void putsTheBearerTokenSubjectIntoTheContextAsLong() {
        McpTransportContext context = extractor.extract(requestWith(bearerToken("42")));

        assertThat(context.get(McpTransportContextKeys.USER_ID)).isEqualTo(42L);
    }

    /** The audit log needs the address whether or not the call turns out to be authorized. */
    @Test
    void putsTheResolvedClientAddressIntoTheContextEvenWithoutAUser() {
        assertThat(extractor.extract(requestWith(bearerToken("42"), "203.0.113.7"))
                .get(McpTransportContextKeys.CLIENT_ADDRESS)).isEqualTo("203.0.113.7");
        assertThat(extractor.extract(requestWith(null, "203.0.113.8"))
                .get(McpTransportContextKeys.CLIENT_ADDRESS)).isEqualTo("203.0.113.8");
    }

    @Test
    void contextHasNoUserIdWithoutPrincipal() {
        McpTransportContext context = extractor.extract(requestWith(null));

        assertThat(context.get(McpTransportContextKeys.USER_ID)).isNull();
    }

    @Test
    void aSessionAuthenticationWithANumericUsernameIsIgnored() {
        var sessionAuthentication = UsernamePasswordAuthenticationToken.authenticated(
                new User("42", "", List.of()), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        McpTransportContext context = extractor.extract(requestWith(sessionAuthentication));

        assertThat(context.get(McpTransportContextKeys.USER_ID)).isNull();
    }

    @Test
    void aPlainPrincipalWithANumericNameIsIgnored() {
        McpTransportContext context = extractor.extract(requestWith(() -> "42"));

        assertThat(context.get(McpTransportContextKeys.USER_ID)).isNull();
    }

    @Test
    void anUnauthenticatedBearerTokenIsIgnored() {
        BearerTokenAuthentication bearerToken = bearerToken("42");
        bearerToken.setAuthenticated(false);

        McpTransportContext context = extractor.extract(requestWith(bearerToken));

        assertThat(context.get(McpTransportContextKeys.USER_ID)).isNull();
    }

    @Test
    void contextHasNoUserIdForBlankOrNonNumericOrNonPositiveSubjects() {
        for (String subject : new String[] {"", "  ", "abc", "4x", "0", "-5", "99999999999999999999"}) {
            McpTransportContext context = extractor.extract(requestWith(bearerToken(subject)));

            assertThat(context.get(McpTransportContextKeys.USER_ID)).as("subject '%s'", subject).isNull();
        }
    }
}
