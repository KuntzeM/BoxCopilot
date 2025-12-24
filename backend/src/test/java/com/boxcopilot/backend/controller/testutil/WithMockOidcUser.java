package com.boxcopilot.backend.testutil;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.test.context.support.WithSecurityContext;
import org.springframework.security.test.context.support.WithSecurityContextFactory;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.Instant;
import java.util.*;

/**
 * Custom annotation to mock an OIDC authenticated user in tests.
 * Usage: @WithMockOidcUser(sub = "test-user", email = "test@example.com")
 */
@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithMockOidcUser.WithMockOidcUserSecurityContextFactory.class)
public @interface WithMockOidcUser {
    
    String sub() default "test-user-123";
    
    String email() default "test@example.com";
    
    String name() default "Test User";
    
    String preferredUsername() default "testuser";

    class WithMockOidcUserSecurityContextFactory implements WithSecurityContextFactory<WithMockOidcUser> {

        @Override
        public SecurityContext createSecurityContext(WithMockOidcUser annotation) {
            SecurityContext context = SecurityContextHolder.createEmptyContext();

            // Create OIDC ID Token claims
            Map<String, Object> claims = new HashMap<>();
            claims.put("sub", annotation.sub());
            claims.put("email", annotation.email());
            claims.put("name", annotation.name());
            claims.put("preferred_username", annotation.preferredUsername());
            claims.put("iat", Instant.now().getEpochSecond());
            claims.put("exp", Instant.now().plusSeconds(3600).getEpochSecond());
            claims.put("iss", "https://cloud.fam-kuntze.de");
            claims.put("aud", Collections.singletonList("test-client"));

            // Create OIDC UserInfo
            Map<String, Object> userInfoClaims = new HashMap<>();
            userInfoClaims.put("sub", annotation.sub());
            userInfoClaims.put("email", annotation.email());
            userInfoClaims.put("name", annotation.name());
            userInfoClaims.put("preferred_username", annotation.preferredUsername());

            OidcUserInfo userInfo = new OidcUserInfo(userInfoClaims);

            // Create OIDC ID Token
            OidcIdToken idToken = new OidcIdToken(
                "mock-token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                claims
            );

            // Create authorities
            Set<GrantedAuthority> authorities = new HashSet<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
            authorities.add(new SimpleGrantedAuthority("SCOPE_openid"));
            authorities.add(new SimpleGrantedAuthority("SCOPE_profile"));
            authorities.add(new SimpleGrantedAuthority("SCOPE_email"));

            // Create OidcUser
            OidcUser oidcUser = new DefaultOidcUser(authorities, idToken, userInfo, "sub");

            // Create Authentication
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                oidcUser,
                null,
                authorities
            );

            context.setAuthentication(authentication);
            return context;
        }
    }
}
