package com.boxcopilot.backend.service;

import com.boxcopilot.backend.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
public class CustomOidcUserService extends OidcUserService {

    private static final Logger log = LoggerFactory.getLogger(CustomOidcUserService.class);
    private static final String ACCESS_DENIED_ERROR_CODE = "access_denied";

    private final UserService userService;
    
    public CustomOidcUserService(UserService userService) {
        this.userService = userService;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        log.debug("Loading OIDC user from identity provider");
        // Delegate to the default implementation for loading the user
        return toLocalUser(super.loadUser(userRequest));
    }

    OidcUser toLocalUser(OidcUser oidcUser) {
        // Extract Nextcloud-specific claims
        Map<String, Object> claims = oidcUser.getClaims();

        String sub = oidcUser.getSubject();
        String email = (String) claims.get("email");
        String name = (String) claims.get("name");
        String preferredUsername = (String) claims.get("preferred_username");

        // Create or update user in database; disabled or locked users are rejected
        User user;
        try {
            user = userService.createOrUpdateOidcUser(sub, preferredUsername, name, email);
        } catch (AccountStatusException ex) {
            throw new OAuth2AuthenticationException(new OAuth2Error(ACCESS_DENIED_ERROR_CODE), ex);
        }

        // Add role-based authorities
        Set<GrantedAuthority> authorities = new HashSet<>(oidcUser.getAuthorities());
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));

        // Return the OidcUser with updated authorities
        return new DefaultOidcUser(
            authorities,
            oidcUser.getIdToken(),
            oidcUser.getUserInfo(),
            "sub"
        );
    }
}
