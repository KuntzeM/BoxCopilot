package com.boxcopilot.backend.mcp.authserver;

import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** The client id is the only unguessable part of a public client, so it is checked byte by byte. */
class McpClientIdGeneratorTest {

    private final McpClientIdGenerator generator = new McpClientIdGenerator();

    @Test
    void carriesFarMoreThanTheRequiredHundredAndTwentyEightBits() {
        byte[] decoded = Base64.getUrlDecoder().decode(generator.newClientId());

        assertThat(decoded).hasSize(32);
    }

    @Test
    void isBase64UrlWithoutPadding() {
        String clientId = generator.newClientId();

        assertThat(clientId).hasSize(43).matches("[A-Za-z0-9_-]+").doesNotContain("=");
    }

    @Test
    void neverRepeatsItself() {
        Set<String> clientIds = new HashSet<>();
        for (int i = 0; i < 2_000; i++) {
            clientIds.add(generator.newClientId());
        }

        assertThat(clientIds).hasSize(2_000);
    }

    @Test
    void doesNotDependOnTheGeneratorInstance() {
        assertThat(new McpClientIdGenerator().newClientId()).isNotEqualTo(new McpClientIdGenerator().newClientId());
    }

    /** A weak generator would repeat single characters; a random 43-character string uses a wide alphabet. */
    @Test
    void usesTheWholeAlphabet() {
        Set<Character> characters = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            for (char c : generator.newClientId().toCharArray()) {
                characters.add(c);
            }
        }

        assertThat(characters).hasSizeGreaterThan(60);
    }
}
