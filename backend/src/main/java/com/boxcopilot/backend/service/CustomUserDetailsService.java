package com.boxcopilot.backend.service;

import com.boxcopilot.backend.domain.AuthProvider;
import com.boxcopilot.backend.domain.Role;
import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;

/**
 * Custom UserDetailsService for local authentication
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {
    
    private static final Logger log = LoggerFactory.getLogger(CustomUserDetailsService.class);
    
    private final UserRepository userRepository;
    
    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("Loading user by username: {}", username);
        
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        
        // Disabled/locked accounts are rejected by Spring (isEnabled/isAccountNonLocked of the principal),
        // which ends in a plain authentication failure instead of an "internal error".
        return new CustomUserPrincipal(user);
    }
    
    /**
     * Custom UserDetails implementation
     */
    public static class CustomUserPrincipal implements UserDetails, CredentialsContainer, Serializable {

        // Bumped when the principal was slimmed (no User entity, no hash): sessions of the old shape fail to deserialize.
        @Serial
        private static final long serialVersionUID = 2L;

        private final Long id;
        private final String username;
        private final String name;
        private final Role role;
        private final AuthProvider authProvider;
        private final boolean enabled;
        private final boolean accountNonLocked;
        /** Reset marker captured at login; compared with the current one to end sessions after a password reset. */
        private final LocalDateTime sessionsValidAfter;
        /** Needed once by DaoAuthenticationProvider; transient so it can never reach the session store. */
        private transient String passwordHash;

        public CustomUserPrincipal(User user) {
            this.id = user.getId();
            this.username = user.getUsername();
            this.name = user.getName();
            this.role = user.getRole();
            this.authProvider = user.getAuthProvider();
            this.enabled = Boolean.TRUE.equals(user.getEnabled());
            this.accountNonLocked = !user.isAccountLocked();
            this.sessionsValidAfter = user.getSessionsValidAfter();
            this.passwordHash = user.getPasswordHash();
        }

        public Long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public Role getRole() {
            return role;
        }

        public AuthProvider getAuthProvider() {
            return authProvider;
        }

        public LocalDateTime getSessionsValidAfter() {
            return sessionsValidAfter;
        }

        @Override
        public void eraseCredentials() {
            passwordHash = null;
        }

        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role.name()));
        }

        @Override
        public String getPassword() {
            return passwordHash;
        }

        @Override
        public String getUsername() {
            return username;
        }

        @Override
        public boolean isAccountNonExpired() {
            return true;
        }

        @Override
        public boolean isAccountNonLocked() {
            return accountNonLocked;
        }

        @Override
        public boolean isCredentialsNonExpired() {
            return true;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }
    }

    /**
     * Exception for disabled accounts (OIDC login path)
     */
    public static class DisabledException extends org.springframework.security.authentication.DisabledException {
        public DisabledException(String message) {
            super(message);
        }
    }
}
