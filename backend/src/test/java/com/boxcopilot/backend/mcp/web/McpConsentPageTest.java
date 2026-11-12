package com.boxcopilot.backend.mcp.web;

import com.boxcopilot.backend.mcp.security.McpOAuth2Attributes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The consent page is assembled by hand, so the escaping of every dynamic value is tested by hand too. */
class McpConsentPageTest {

    private static McpConsentPage.View view(String username) {
        return new McpConsentPage.View(username, "claude.ai", List.of(McpOAuth2Attributes.SCOPE_READ),
                "client-id-value", "state-value", "_csrf", "csrf-token-value");
    }

    @Test
    void showsTheUserTheScopeInPlainLanguageAndBothButtons() {
        String html = McpConsentPage.render(view("alice"));

        assertThat(html).contains("alice")
                .contains(McpConsentPage.CLIENT_NAME)
                .contains("claude.ai")
                .contains("Lesezugriff auf Ihre Boxen und Gegenstände")
                .contains("Namen, Beschreibungen und Bilder")
                .contains("Nur bestätigen, wenn Sie diese Verbindung gerade selbst in Claude gestartet")
                .contains(">Bestätigen<")
                .contains(">Ablehnen<");
    }

    @Test
    void submitsToTheAuthorizeEndpointWithClientIdStateScopeAndTheCsrfToken() {
        String html = McpConsentPage.render(view("alice"));

        assertThat(html).contains("action=\"/oauth2/authorize\"")
                .contains("name=\"client_id\" value=\"client-id-value\"")
                .contains("name=\"state\" value=\"state-value\"")
                .contains("name=\"scope\" value=\"mcp:read\"")
                .contains("name=\"_csrf\" value=\"csrf-token-value\"");
    }

    /** Declining means submitting no scope at all, which the authorization server answers with access_denied. */
    @Test
    void theDeclineFormCarriesNoScope() {
        String html = McpConsentPage.render(view("alice"));

        String declineForm = html.substring(html.lastIndexOf("<form"));
        assertThat(declineForm).contains(">Ablehnen<").doesNotContain("name=\"scope\"");
    }

    @Test
    void aUserNameIsEscapedRatherThanRendered() {
        String html = McpConsentPage.render(view("<script>alert('x')</script>\" onmouseover=\"y"));

        assertThat(html).doesNotContain("<script>").doesNotContain("onmouseover=\"y");
        assertThat(html).contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;")
                .contains("&quot; onmouseover=&quot;y");
    }

    @Test
    void anAmpersandIsEscapedExactlyOnce() {
        String html = McpConsentPage.render(view("a&b"));

        assertThat(html).contains("a&amp;b").doesNotContain("a&amp;amp;b");
    }
}
