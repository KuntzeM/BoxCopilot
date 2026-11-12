package com.boxcopilot.backend.mcp.web;

import com.boxcopilot.backend.mcp.authserver.McpAuthorizationServerEndpoints;
import com.boxcopilot.backend.mcp.security.McpOAuth2Attributes;

import java.util.List;

/**
 * Renders the consent page as plain, server-side HTML. There is no template engine and no styling, because the
 * page's content security policy forbids loading anything and forbids inline styles.
 *
 * <p>Every dynamic value passes through {@link McpSecureHtmlResponse#escape(String)}: the user name is chosen by
 * an administrator and the redirect host comes from configuration, but escaping them is what keeps the page safe
 * no matter where those values come from later.
 *
 * <p>The displayed user name is the last line of defence against a login-CSRF: an attacker who logged the victim
 * into his own account can only obtain consent for that account, and the name on the page shows it.
 */
public final class McpConsentPage {

    /** No client name is ever taken from a client; every connection is presented the same way. */
    public static final String CLIENT_NAME = "Claude-Verbindung";

    private McpConsentPage() {
    }

    /**
     * @param username          the name of the logged-in user, read fresh from the database
     * @param redirectHost      the host the authorization code would be sent to
     * @param scopes            the requested scopes, in the order they should be shown
     * @param clientId          value of the hidden {@code client_id} field
     * @param state             value of the hidden {@code state} field
     * @param csrfParameterName the form field the CSRF token belongs in
     * @param csrfToken         the CSRF token of this request
     */
    public record View(String username, String redirectHost, List<String> scopes, String clientId, String state,
                       String csrfParameterName, String csrfToken) {
    }

    public static String render(View view) {
        StringBuilder html = new StringBuilder(2048);
        html.append("""
                <!DOCTYPE html>
                <html lang="de">
                <head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Claude-Verbindung bestätigen</title></head>
                <body>
                <h1>Claude-Verbindung bestätigen</h1>
                """);
        html.append("<p><strong>").append(escape(CLIENT_NAME))
                .append("</strong> möchte auf Ihr BoxPilot-Inventar zugreifen.</p>\n");
        html.append("<p>Angemeldet als: <strong>").append(escape(view.username())).append("</strong></p>\n");
        html.append("<p>Weiterleitung an: <strong>").append(escape(view.redirectHost())).append("</strong></p>\n");
        html.append("<h2>Berechtigungen</h2>\n<ul>\n");
        for (String scope : view.scopes()) {
            html.append("<li>").append(escape(describe(scope))).append("</li>\n");
        }
        html.append("</ul>\n");
        html.append("""
                <p>Dabei werden Daten aus Ihrem Inventar - Namen, Beschreibungen und Bilder - an den
                KI-Anbieter übertragen.</p>
                <p><strong>Nur bestätigen, wenn Sie diese Verbindung gerade selbst in Claude gestartet
                haben.</strong></p>
                """);
        html.append(form(view, true)).append(form(view, false));
        html.append("</body>\n</html>\n");
        return html.toString();
    }

    /**
     * Approval and decline are two separate forms, because declining means submitting <em>no</em> scope at all:
     * Spring Authorization Server answers a consent without approved scopes with {@code access_denied}. A single
     * form could not leave the scope fields out for one of its buttons.
     */
    private static String form(View view, boolean approve) {
        StringBuilder form = new StringBuilder(512);
        form.append("<form method=\"post\" action=\"").append(escape(McpAuthorizationServerEndpoints.AUTHORIZE))
                .append("\">\n");
        form.append(hidden("client_id", view.clientId()));
        form.append(hidden("state", view.state()));
        if (approve) {
            for (String scope : view.scopes()) {
                form.append(hidden("scope", scope));
            }
        }
        form.append(hidden(view.csrfParameterName(), view.csrfToken()));
        form.append("<button type=\"submit\">").append(approve ? "Bestätigen" : "Ablehnen").append("</button>\n");
        form.append("</form>\n");
        return form.toString();
    }

    private static String hidden(String name, String value) {
        return "<input type=\"hidden\" name=\"" + escape(name) + "\" value=\"" + escape(value) + "\">\n";
    }

    private static String describe(String scope) {
        if (McpOAuth2Attributes.SCOPE_READ.equals(scope)) {
            return "Lesezugriff auf Ihre Boxen und Gegenstände";
        }
        return scope;
    }

    private static String escape(String value) {
        return McpSecureHtmlResponse.escape(value);
    }
}
