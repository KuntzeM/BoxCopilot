package com.boxcopilot.backend.config;

import com.boxcopilot.backend.domain.AuthProvider;
import com.boxcopilot.backend.domain.Role;
import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.repository.UserRepository;
import com.boxcopilot.backend.service.CustomUserDetailsService.CustomUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionRevalidationFilterTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final SessionRevalidationFilter filter = new SessionRevalidationFilter(userRepository);
    private MockHttpSession session;
    private MockHttpServletRequest request;
    private User sessionUser;

    @BeforeEach
    void setUp() {
        sessionUser = new User("admin", "Admin", AuthProvider.LOCAL, Role.ADMIN);
        sessionUser.setId(1L);
        sessionUser.setEnabled(true);
        loginAs(sessionUser);
        session = new MockHttpSession();
        request = new MockHttpServletRequest();
        request.setSession(session);
    }

    /** The principal snapshots the user (incl. reset marker) when it is built, as at login. */
    private static void loginAs(User user) {
        CustomUserPrincipal principal = new CustomUserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void run() throws Exception {
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }

    private User stored(Role role, boolean enabled) {
        User stored = new User("admin", "Admin", AuthProvider.LOCAL, role);
        stored.setId(1L);
        stored.setEnabled(enabled);
        return stored;
    }

    @Test
    void unchangedUserKeepsSession() throws Exception {
        when(userRepository.findById(1L)).thenReturn(Optional.of(stored(Role.ADMIN, true)));

        run();

        assertFalse(session.isInvalid());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void demotedUserLosesSession() throws Exception {
        when(userRepository.findById(1L)).thenReturn(Optional.of(stored(Role.USER, true)));

        run();

        assertTrue(session.isInvalid());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void disabledUserLosesSession() throws Exception {
        when(userRepository.findById(1L)).thenReturn(Optional.of(stored(Role.ADMIN, false)));

        run();

        assertTrue(session.isInvalid());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void passwordResetSinceLoginEndsSession() throws Exception {
        User reset = stored(Role.ADMIN, true);
        reset.setSessionsValidAfter(java.time.LocalDateTime.now());
        when(userRepository.findById(1L)).thenReturn(Optional.of(reset));

        run();

        assertTrue(session.isInvalid());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void sessionStartedAfterTheResetKeepsItself() throws Exception {
        java.time.LocalDateTime marker = java.time.LocalDateTime.now();
        sessionUser.setSessionsValidAfter(marker);
        loginAs(sessionUser); // snapshot taken by a login after the reset
        User reset = stored(Role.ADMIN, true);
        reset.setSessionsValidAfter(marker);
        when(userRepository.findById(1L)).thenReturn(Optional.of(reset));

        run();

        assertFalse(session.isInvalid());
    }

    @Test
    void deletedUserLosesSession() throws Exception {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        run();

        assertTrue(session.isInvalid());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
