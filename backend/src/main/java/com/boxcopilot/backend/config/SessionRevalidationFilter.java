package com.boxcopilot.backend.config;

import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.repository.UserRepository;
import com.boxcopilot.backend.service.CustomUserDetailsService.CustomUserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Drops a browser session whose user was deleted, disabled, given a different role or had its password reset since login.
 * The session holds a login-time principal snapshot, so without this check an admin action such as a
 * demotion would only take effect once the session expires.
 */
public class SessionRevalidationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SessionRevalidationFilter.class);

    private final UserRepository userRepository;

    public SessionRevalidationFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && isStale(authentication)) {
            log.info("Ending session: user was deleted, disabled, changed role or password reset");
            SecurityContextHolder.clearContext();
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
        }
        chain.doFilter(request, response);
    }

    private boolean isStale(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        Optional<User> current;
        if (principal instanceof CustomUserPrincipal local) {
            current = userRepository.findById(local.getId());
        } else if (principal instanceof OidcUser oidc) {
            current = userRepository.findByOidcSubject(oidc.getSubject());
        } else {
            return false;
        }
        if (current.isEmpty() || !Boolean.TRUE.equals(current.get().getEnabled())) {
            return true;
        }
        String role = "ROLE_" + current.get().getRole().name();
        if (principal instanceof CustomUserPrincipal local && current.get().passwordResetSince(local.getSessionsValidAfter())) {
            return true;
        }
        return authentication.getAuthorities().stream().noneMatch(a -> role.equals(a.getAuthority()));
    }
}
