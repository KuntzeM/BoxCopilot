package com.boxcopilot.backend.mcp.authserver;

import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;

/**
 * A consent store that never remembers anything. Spring Authorization Server skips the consent step whenever it
 * finds a stored consent, so forgetting every consent is what makes the consent page appear on <em>every</em>
 * authorization. There is deliberately no {@code oauth2_authorization_consent} table.
 */
class McpNoOpAuthorizationConsentService implements OAuth2AuthorizationConsentService {

    @Override
    public void save(OAuth2AuthorizationConsent authorizationConsent) {
        // Discarded on purpose: a remembered consent would let a later authorization skip the consent page.
    }

    @Override
    public void remove(OAuth2AuthorizationConsent authorizationConsent) {
        // Nothing is stored, so nothing can be removed.
    }

    @Override
    public OAuth2AuthorizationConsent findById(String registeredClientId, String principalName) {
        return null;
    }
}
