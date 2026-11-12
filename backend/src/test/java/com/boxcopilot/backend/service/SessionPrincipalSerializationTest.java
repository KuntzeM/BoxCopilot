package com.boxcopilot.backend.service;

import com.boxcopilot.backend.domain.AuthProvider;
import com.boxcopilot.backend.domain.Role;
import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.repository.UserRepository;
import com.boxcopilot.backend.service.CustomUserDetailsService.CustomUserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What the session store writes (JDK serialization, like the Redis and JDBC session stores) for a logged-in user
 * must contain neither the bcrypt hash nor the {@code User} entity - for form login and for the magic-link login,
 * which builds its authentication by hand.
 */
class SessionPrincipalSerializationTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final User user = newUser();

    private User newUser() {
        User u = new User("alice", "Alice", AuthProvider.LOCAL, Role.ADMIN);
        u.setId(7L);
        u.setEnabled(true);
        u.setPasswordHash(encoder.encode("correct horse battery"));
        u.setSessionsValidAfter(LocalDateTime.of(2026, 11, 10, 12, 0));
        return u;
    }

    private byte[] sessionBytes(Authentication authentication) {
        SecurityContext context = new SecurityContextImpl(authentication);
        return (byte[]) new JdkSerializationRedisSerializer().serialize(context);
    }

    private void assertSlim(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.ISO_8859_1);
        assertThat(text).doesNotContain("$2a$", "$2b$", user.getPasswordHash(), "domain.User;", "passwordHash");
        SecurityContext back = (SecurityContext) new JdkSerializationRedisSerializer().deserialize(bytes);
        CustomUserPrincipal principal = (CustomUserPrincipal) back.getAuthentication().getPrincipal();
        assertThat(principal.getId()).isEqualTo(7L);
        assertThat(principal.getUsername()).isEqualTo("alice");
        assertThat(principal.getRole()).isEqualTo(Role.ADMIN);
        assertThat(principal.isEnabled()).isTrue();
        assertThat(principal.getSessionsValidAfter()).isEqualTo(user.getSessionsValidAfter());
        assertThat(principal.getPassword()).isNull();
    }

    @Test
    void formLoginSessionHoldsNoHashAndNoUserEntity() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.findByUsername("alice")).thenReturn(Optional.of(user));
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(new CustomUserDetailsService(repository));
        provider.setPasswordEncoder(encoder);

        // ProviderManager erases credentials of the result by default (CredentialsContainer principal)
        Authentication result = new ProviderManager(provider)
                .authenticate(UsernamePasswordAuthenticationToken.unauthenticated("alice", "correct horse battery"));

        assertThat(result.isAuthenticated()).isTrue();
        assertThat(((CustomUserPrincipal) result.getPrincipal()).getPassword()).isNull();
        assertSlim(sessionBytes(result));
    }

    @Test
    void magicLinkSessionHoldsNoHashAndNoUserEntity() {
        CustomUserPrincipal principal = new CustomUserPrincipal(user); // as MagicLoginController builds it

        assertSlim(sessionBytes(UsernamePasswordAuthenticationToken.authenticated(principal, null,
                principal.getAuthorities())));
    }
}
