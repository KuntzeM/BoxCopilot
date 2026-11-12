package com.boxcopilot.backend.mcp;

import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.mcp.authserver.McpAuthorizationServerEndpoints;
import com.boxcopilot.backend.mcp.web.McpPendingCookies;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * The cookie that carries an interrupted authorization across the login, and the {@code continue} endpoint that
 * replays it. Nothing here may create server-side state.
 */
class McpPendingAuthorizationIntegrationTest extends McpIntegrationTestBase {

    private static final String FRONTEND_URL = "http://localhost:3000/";

    private String authorizeQuery;

    /**
     * The entry point is only reached once the request itself is valid: an unknown client id is rejected before
     * the login check, so the pending cookie needs a genuinely registered client.
     */
    @BeforeEach
    void registerAClientToAuthorize() {
        User owner = createUser(true, true);
        this.authorizeQuery = "response_type=code&client_id=" + createClient(owner.getId()).getClientId()
                + "&redirect_uri=https%3A%2F%2Fclaude.ai%2Fapi%2Fmcp%2Fauth_callback"
                + "&state=xyz&code_challenge=cc&code_challenge_method=S256";
    }

    // ---------------------------------------------------------------- the entry point

    @Test
    void anAnonymousAuthorizeRequestStoresTheQueryInACookieAndSendsTheBrowserToTheFrontend() throws Exception {
        MvcResult result = anonymousAuthorize(this.authorizeQuery);

        assertThat(result.getResponse().getStatus()).isEqualTo(302);
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo(FRONTEND_URL);
        String setCookie = pendingCookieHeader(result);
        assertThat(setCookie).contains("HttpOnly").contains("Secure").contains("Path=/")
                .contains("Max-Age=600").contains("SameSite=Lax");
        assertThat(McpPendingCookies.decode(pendingCookieValue(result))).contains(this.authorizeQuery);
    }

    @Test
    void theCookieNeverCarriesTheQueryInClearText() throws Exception {
        MvcResult result = anonymousAuthorize(this.authorizeQuery);

        assertThat(pendingCookieValue(result)).doesNotContain("client_id", "=", "&");
    }

    @Test
    void aSecondAuthorizeRequestOverwritesThePendingOne() throws Exception {
        MvcResult first = anonymousAuthorize(this.authorizeQuery);
        MvcResult second = anonymousAuthorize(this.authorizeQuery + "&prompt=second");

        assertThat(pendingCookieValue(first)).isNotEqualTo(pendingCookieValue(second));
        assertThat(McpPendingCookies.decode(pendingCookieValue(second)).orElseThrow()).endsWith("&prompt=second");
    }

    @Test
    void anOversizedAuthorizeQueryIsRejectedWithoutACookie() throws Exception {
        MvcResult result = anonymousAuthorize(this.authorizeQuery + "&state=" + "x".repeat(2048));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
    }

    @Test
    void aFloodOfAnonymousAuthorizeRequestsCreatesNoSession() throws Exception {
        for (int i = 0; i < 25; i++) {
            MvcResult result = anonymousAuthorize(this.authorizeQuery + "&state=flood-" + i);

            assertThat(result.getRequest().getSession(false)).as("request %s", i).isNull();
            assertThat(result.getResponse().getCookie("JSESSIONID")).as("request %s", i).isNull();
        }
    }

    // ---------------------------------------------------------------- /api/v1/me

    @Test
    void onlyAnAuthenticatedCallerLearnsThatAnAuthorizationIsPending() throws Exception {
        User user = createUser(true, true);
        Cookie pending = new Cookie(McpPendingCookies.NAME, McpPendingCookies.encode(this.authorizeQuery));

        MvcResult anonymous = mockMvc.perform(get("/api/v1/me").cookie(pending)).andReturn();
        MvcResult withPending = mockMvc.perform(get("/api/v1/me").session(sessionFor(user)).cookie(pending))
                .andReturn();
        MvcResult withoutPending = mockMvc.perform(get("/api/v1/me").session(sessionFor(user))).andReturn();

        assertThat(anonymous.getResponse().getContentAsString()).doesNotContain("mcpPending");
        assertThat(withPending.getResponse().getContentAsString()).contains("\"mcpPending\":true");
        assertThat(withoutPending.getResponse().getContentAsString()).contains("\"mcpPending\":false");
    }

    // ---------------------------------------------------------------- continue

    @Test
    void continueReplaysThePendingQueryAgainstTheConfiguredBackendUrlAndClearsTheCookie() throws Exception {
        User user = createUser(true, true);

        MvcResult result = resume(sessionFor(user), McpPendingCookies.encode(this.authorizeQuery));

        assertThat(result.getResponse().getStatus()).isEqualTo(302);
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
                .isEqualTo("http://localhost" + McpAuthorizationServerEndpoints.AUTHORIZE + "?" + this.authorizeQuery);
        assertThat(pendingCookieHeader(result)).contains("Max-Age=0").contains("HttpOnly").contains("Secure")
                .contains("Path=/");
    }

    @Test
    void continueWithoutASessionSendsTheBrowserToTheFrontendAndRevealsNothing() throws Exception {
        MvcResult result = resume(null, McpPendingCookies.encode(this.authorizeQuery));

        assertThat(result.getResponse().getStatus()).isEqualTo(302);
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo(FRONTEND_URL);
        assertThat(result.getResponse().getContentAsString()).isEmpty();
    }

    @Test
    void continueWithoutACookieSendsTheBrowserToTheFrontend() throws Exception {
        User user = createUser(true, true);

        MvcResult result = resume(sessionFor(user), null);

        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo(FRONTEND_URL);
    }

    @Test
    void continueNeverRedirectsToAQueryThatIsNotPrintableAsciiOrCarriesAFragment() throws Exception {
        User user = createUser(true, true);
        List<String> unusable = List.of(
                McpPendingCookies.encode("state=1#fragment"),
                McpPendingCookies.encode("state=äöü"),
                McpPendingCookies.encode("state=\u0000"),
                "not-base64url!!");

        for (String value : unusable) {
            MvcResult result = resume(sessionFor(user), value);

            assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).as(value).isEqualTo(FRONTEND_URL);
            assertThat(pendingCookieHeader(result)).as(value).contains("Max-Age=0");
        }
    }

    @Test
    void continueCannotBeTalkedIntoRedirectingToAForeignHost() throws Exception {
        User user = createUser(true, true);

        MvcResult result = resume(sessionFor(user),
                McpPendingCookies.encode("https://evil.example.com/steal?x=1"));

        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
                .startsWith("http://localhost" + McpAuthorizationServerEndpoints.AUTHORIZE + "?")
                .doesNotStartWith("https://evil.example.com");
    }

    @Test
    void continueOfADisabledUserDoesNotReplayTheAuthorization() throws Exception {
        User user = createUser(false, true);

        MvcResult result = resume(sessionFor(user), McpPendingCookies.encode(this.authorizeQuery));

        // The session of a disabled user is ended before the handler runs, so the authorization is not replayed
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
                .doesNotStartWith("http://localhost" + McpAuthorizationServerEndpoints.AUTHORIZE);
    }

    // ---------------------------------------------------------------- helpers

    private MvcResult anonymousAuthorize(String query) throws Exception {
        return mockMvc.perform(get(URI.create(McpAuthorizationServerEndpoints.AUTHORIZE + "?" + query))).andReturn();
    }

    private MvcResult resume(MockHttpSession session, String cookieValue) throws Exception {
        var request = get(McpEndpoints.CONTINUE_PENDING_AUTHORIZATION);
        if (session != null) {
            request = request.session(session);
        }
        if (cookieValue != null) {
            request = request.cookie(new Cookie(McpPendingCookies.NAME, cookieValue));
        }
        return mockMvc.perform(request).andReturn();
    }

    private static String pendingCookieHeader(MvcResult result) {
        return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(header -> header.startsWith(McpPendingCookies.NAME + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + McpPendingCookies.NAME + " cookie was set"));
    }

    private static String pendingCookieValue(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(McpPendingCookies.NAME);
        assertThat(cookie).isNotNull();
        return cookie.getValue();
    }
}
