package com.boxcopilot.backend.mcp.authserver;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The pre-select pattern, without a database: it has to match the stored binding of exactly one user, and the
 * wildcards {@code LIKE} would otherwise read inside the fragment have to be escaped.
 */
class McpUserClientsTest {

    @Test
    void thePatternQuotesTheBindingOfThatUser() {
        assertThat(McpUserClients.bindingPattern(42))
                .isEqualTo("%\"boxpilot.user!_id\":\"42\"%");
    }

    /** {@code _} is "any character" in LIKE; unescaped it would also match "boxpilotXuser_id"-style values. */
    @Test
    void theUnderscoreOfTheSettingNameIsEscaped() {
        String pattern = McpUserClients.bindingPattern(7);

        assertThat(pattern).contains(McpUserClients.LIKE_ESCAPE + "_");
        assertThat(pattern.replace(McpUserClients.LIKE_ESCAPE + "_", "_")).doesNotContain(
                McpUserClients.LIKE_ESCAPE);
    }

    /** Only the two outer wildcards remain; everything between them is a literal. */
    @Test
    void onlyTheEndsAreWildcards() {
        String pattern = McpUserClients.bindingPattern(1);

        assertThat(pattern).startsWith("%").endsWith("%");
        assertThat(pattern.substring(1, pattern.length() - 1)).doesNotContain("%");
    }

    @Test
    void oneUserCannotMatchTheBindingOfAnother() {
        assertThat(McpUserClients.bindingPattern(42)).isNotEqualTo(McpUserClients.bindingPattern(421));
        assertThat(McpUserClients.bindingPattern(42)).endsWith("\"42\"%");
    }

    @Test
    void theEscapeCharacterIsDeclaredInTheStatement() {
        assertThat(McpUserClients.SELECT_IDS_BY_BINDING)
                .contains("LIKE ? ESCAPE '" + McpUserClients.LIKE_ESCAPE + "'");
    }
}
