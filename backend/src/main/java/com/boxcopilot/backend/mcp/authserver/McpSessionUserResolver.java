package com.boxcopilot.backend.mcp.authserver;

import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.repository.UserRepository;
import com.boxcopilot.backend.service.CustomUserDetailsService.CustomUserPrincipal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Turns the application's session authentication into the current database state of that user.
 *
 * <p>The session carries a {@code CustomUserPrincipal} with identity and reset marker only (form login and magic link) or
 * an {@code OidcUser} (Nextcloud). Both are snapshots taken at login time and may be hours old, so nothing but
 * the identity is taken from them: the user is always reloaded, which is what makes a disabled, locked or
 * un-allowed user fail at the authorize endpoint rather than at the next login.
 */
@Component
@ConditionalOnProperty(prefix = "app.mcp", name = "enabled", havingValue = "true")
public class McpSessionUserResolver {

    private final UserRepository userRepository;

    public McpSessionUserResolver(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** @return the current state of the authenticated user, or empty for anonymous or unknown principals */
    public Optional<User> resolve(Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserPrincipal customPrincipal) {
            Long id = customPrincipal.getId();
            return id == null ? Optional.empty() : userRepository.findById(id)
                    .filter(current -> !current.passwordResetSince(customPrincipal.getSessionsValidAfter()));
        }
        if (principal instanceof OidcUser oidcUser) {
            return userRepository.findByOidcSubject(oidcUser.getSubject());
        }
        return Optional.empty();
    }

    public static boolean isAuthenticated(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
