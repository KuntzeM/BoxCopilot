package com.boxcopilot.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CustomOidcUserService extends OidcUserService {

    private static final Logger log = LoggerFactory.getLogger(CustomOidcUserService.class);

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        log.debug("Loading OIDC user from identity provider");
        // Delegate to the default implementation for loading the user
        OidcUser oidcUser = super.loadUser(userRequest);

        // Extract Nextcloud-specific claims
        Map<String, Object> claims = oidcUser.getClaims();
        
        // Log the user attributes for debugging
        String sub = oidcUser.getSubject();
        String email = (String) claims.get("email");
        String name = (String) claims.get("name");
        String preferredUsername = (String) claims.get("preferred_username");

        // Log user authentication
        log.info("OIDC user authenticated - username: {}, email: {}, subject: {}", preferredUsername, email, sub);

        // Return the OidcUser with all claims intact
        // The nameAttributeKey is "sub" by default for OIDC
        return new DefaultOidcUser(
            oidcUser.getAuthorities(),
            oidcUser.getIdToken(),
            oidcUser.getUserInfo(),
            "sub"
        );
    }
}
