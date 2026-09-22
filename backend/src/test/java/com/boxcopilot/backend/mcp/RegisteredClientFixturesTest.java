package com.boxcopilot.backend.mcp;

import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The fixture is the template story S9 registers real clients from, so the client id it produces has to meet the
 * specification on its own: at least 128 bits from {@code SecureRandom}, Base64url encoded.
 */
class RegisteredClientFixturesTest {

    private static final int MINIMUM_BYTES = 16;

    @Test
    void aClientIdCarriesAtLeastOneHundredAndTwentyEightBits() {
        byte[] decoded = Base64.getUrlDecoder().decode(RegisteredClientFixtures.randomClientId());

        assertThat(decoded.length).isGreaterThanOrEqualTo(MINIMUM_BYTES);
    }

    @Test
    void aClientIdIsBase64UrlWithoutPaddingSoItSurvivesAQueryString() {
        String clientId = RegisteredClientFixtures.randomClientId();

        assertThat(clientId).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void everyClientIdIsDifferent() {
        Set<String> clientIds = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            clientIds.add(RegisteredClientFixtures.randomClientId());
        }

        assertThat(clientIds).hasSize(500);
    }

    @Test
    void theCodeChallengeIsTheBase64UrlSha256OfTheVerifier() {
        // RFC 7636 appendix B
        assertThat(RegisteredClientFixtures.codeChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
                .isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
    }
}
