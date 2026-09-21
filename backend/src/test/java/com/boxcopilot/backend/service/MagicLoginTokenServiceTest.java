package com.boxcopilot.backend.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.boxcopilot.backend.domain.AuthProvider;
import com.boxcopilot.backend.domain.MagicLoginToken;
import com.boxcopilot.backend.domain.Role;
import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.dto.CreateMagicLinkRequestDTO;
import com.boxcopilot.backend.dto.MagicLinkResponseDTO;
import com.boxcopilot.backend.repository.MagicLoginTokenRepository;
import com.boxcopilot.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MagicLoginTokenServiceTest {

    private static final String FRONTEND_URL = "https://boxpilot.example.com";

    @Mock
    private MagicLoginTokenRepository tokenRepository;

    @Mock
    private UserRepository userRepository;

    private MagicLoginTokenService magicLoginTokenService;

    private ListAppender<ILoggingEvent> logAppender;
    private Logger serviceLogger;

    private User testUser;
    private MagicLoginToken testToken;

    @BeforeEach
    void setUp() {
        magicLoginTokenService = new MagicLoginTokenService(tokenRepository, userRepository, FRONTEND_URL, 24);

        serviceLogger = (Logger) LoggerFactory.getLogger(MagicLoginTokenService.class);
        serviceLogger.setLevel(Level.TRACE);
        logAppender = new ListAppender<>();
        logAppender.start();
        serviceLogger.addAppender(logAppender);

        testUser = new User("testuser", "Test User", AuthProvider.LOCAL, Role.USER);
        testUser.setId(1L);
        testUser.setEnabled(true);

        testToken = new MagicLoginToken(UUID.randomUUID().toString(), testUser, 24);
        testToken.setId(1L);
    }

    @AfterEach
    void tearDownLogCapture() {
        serviceLogger.detachAppender(logAppender);
    }

    @Test
    void testGenerateMagicLink_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(tokenRepository.findByUserAndExpiryDateAfter(eq(testUser), any(LocalDateTime.class)))
            .thenReturn(new ArrayList<>());
        when(tokenRepository.save(any(MagicLoginToken.class))).thenReturn(testToken);

        MagicLinkResponseDTO result = magicLoginTokenService.generateMagicLink(
            1L, new CreateMagicLinkRequestDTO(), "admin"
        );

        assertNotNull(result);
        assertNotNull(result.getToken());
        assertNotNull(result.getExpiresAt());
        assertEquals(FRONTEND_URL + "/magic#t=" + result.getToken(), result.getUrl());
        verify(tokenRepository).save(any(MagicLoginToken.class));
    }

    @Test
    void generateMagicLinkStripsTrailingSlashFromFrontendUrl() {
        MagicLoginTokenService service =
            new MagicLoginTokenService(tokenRepository, userRepository, FRONTEND_URL + "/", 24);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(tokenRepository.findByUserAndExpiryDateAfter(eq(testUser), any(LocalDateTime.class)))
            .thenReturn(new ArrayList<>());
        when(tokenRepository.save(any(MagicLoginToken.class))).thenReturn(testToken);

        MagicLinkResponseDTO result = service.generateMagicLink(1L, null, "admin");

        assertEquals(FRONTEND_URL + "/magic#t=" + result.getToken(), result.getUrl());
    }

    @Test
    void constructionFailsWhenFrontendUrlIsBlank() {
        assertThrows(IllegalStateException.class,
            () -> new MagicLoginTokenService(tokenRepository, userRepository, "  ", 24));
        assertThrows(IllegalStateException.class,
            () -> new MagicLoginTokenService(tokenRepository, userRepository, "", 24));
        assertThrows(IllegalStateException.class,
            () -> new MagicLoginTokenService(tokenRepository, userRepository, null, 24));
    }

    @Test
    void generateMagicLinkRejectsExpiryAboveMaximum() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        assertThrows(IllegalArgumentException.class,
            () -> magicLoginTokenService.generateMagicLink(1L, new CreateMagicLinkRequestDTO(73), "admin"));
        verify(tokenRepository, never()).save(any(MagicLoginToken.class));
    }

    @Test
    void generateMagicLinkAcceptsMaximumExpiry() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(tokenRepository.findByUserAndExpiryDateAfter(eq(testUser), any(LocalDateTime.class)))
            .thenReturn(new ArrayList<>());
        when(tokenRepository.save(any(MagicLoginToken.class))).thenReturn(testToken);

        assertNotNull(magicLoginTokenService.generateMagicLink(1L, new CreateMagicLinkRequestDTO(72), "admin"));
    }

    @Test
    void generateMagicLinkRejectsNonPositiveExpiry() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        assertThrows(IllegalArgumentException.class,
            () -> magicLoginTokenService.generateMagicLink(1L, new CreateMagicLinkRequestDTO(0), "admin"));
    }

    @Test
    void testGenerateMagicLink_WithCustomExpiry() {
        CreateMagicLinkRequestDTO request = new CreateMagicLinkRequestDTO(48);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(tokenRepository.findByUserAndExpiryDateAfter(eq(testUser), any(LocalDateTime.class)))
            .thenReturn(new ArrayList<>());

        MagicLoginToken tokenWith48Hours = new MagicLoginToken(UUID.randomUUID().toString(), testUser, 48);
        when(tokenRepository.save(any(MagicLoginToken.class))).thenReturn(tokenWith48Hours);

        MagicLinkResponseDTO result = magicLoginTokenService.generateMagicLink(
            1L, request, "admin"
        );

        assertNotNull(result);
        verify(tokenRepository).save(any(MagicLoginToken.class));
    }

    @Test
    void testGenerateMagicLink_InvalidatesOldTokens() {
        List<MagicLoginToken> existingTokens = List.of(testToken);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(tokenRepository.findByUserAndExpiryDateAfter(eq(testUser), any(LocalDateTime.class)))
            .thenReturn(existingTokens);
        when(tokenRepository.save(any(MagicLoginToken.class))).thenReturn(testToken);

        magicLoginTokenService.generateMagicLink(1L, new CreateMagicLinkRequestDTO(), "admin");

        verify(tokenRepository).deleteAll(existingTokens);
    }

    @Test
    void consumeReturnsUserAfterAtomicConsume() {
        String tokenString = testToken.getToken();
        when(tokenRepository.findByToken(tokenString)).thenReturn(Optional.of(testToken));
        when(tokenRepository.consume(eq(tokenString), any(LocalDateTime.class))).thenReturn(1);

        Optional<User> result = magicLoginTokenService.consumeToken(tokenString);

        assertTrue(result.isPresent());
        assertEquals(testUser, result.get());
        verify(tokenRepository, never()).save(any(MagicLoginToken.class));
    }

    @Test
    void consumeIsDeniedWhenAtomicConsumeAffectsNoRow() {
        when(tokenRepository.findByToken(testToken.getToken())).thenReturn(Optional.of(testToken));
        when(tokenRepository.consume(eq(testToken.getToken()), any(LocalDateTime.class))).thenReturn(0);

        assertFalse(magicLoginTokenService.consumeToken(testToken.getToken()).isPresent());
    }

    @Test
    void consumeIsDeniedForUnknownToken() {
        when(tokenRepository.findByToken("nonexistent-token")).thenReturn(Optional.empty());

        assertFalse(magicLoginTokenService.consumeToken("nonexistent-token").isPresent());
        verify(tokenRepository, never()).consume(anyString(), any(LocalDateTime.class));
    }

    @Test
    void consumeIsDeniedForExpiredTokenByConsumeResult() {
        MagicLoginToken expiredToken = new MagicLoginToken(UUID.randomUUID().toString(), testUser, 24);
        expiredToken.setExpiryDate(LocalDateTime.now().minusHours(1));
        when(tokenRepository.findByToken(expiredToken.getToken())).thenReturn(Optional.of(expiredToken));
        when(tokenRepository.consume(eq(expiredToken.getToken()), any(LocalDateTime.class))).thenReturn(0);

        assertFalse(magicLoginTokenService.consumeToken(expiredToken.getToken()).isPresent());
    }

    @Test
    void consumeDoesNotConsumeTokenOfDisabledUser() {
        testUser.setEnabled(false);
        when(tokenRepository.findByToken(testToken.getToken())).thenReturn(Optional.of(testToken));

        assertFalse(magicLoginTokenService.consumeToken(testToken.getToken()).isPresent());
        verify(tokenRepository, never()).consume(anyString(), any(LocalDateTime.class));
    }

    @Test
    void consumeDoesNotConsumeTokenOfLockedUser() {
        testUser.setLockedUntil(LocalDateTime.now().plusHours(1));
        when(tokenRepository.findByToken(testToken.getToken())).thenReturn(Optional.of(testToken));

        assertFalse(magicLoginTokenService.consumeToken(testToken.getToken()).isPresent());
        verify(tokenRepository, never()).consume(anyString(), any(LocalDateTime.class));
    }

    @Test
    void consumeAcceptsUserWhoseLockHasExpired() {
        testUser.setLockedUntil(LocalDateTime.now().minusMinutes(1));
        when(tokenRepository.findByToken(testToken.getToken())).thenReturn(Optional.of(testToken));
        when(tokenRepository.consume(eq(testToken.getToken()), any(LocalDateTime.class))).thenReturn(1);

        assertTrue(magicLoginTokenService.consumeToken(testToken.getToken()).isPresent());
    }

    @Test
    void logsNeverContainTheTokenOrItsPrefix() {
        String tokenString = testToken.getToken();
        String prefix = tokenString.substring(0, 8);
        when(tokenRepository.findByToken(tokenString)).thenReturn(Optional.of(testToken));
        when(tokenRepository.consume(eq(tokenString), any(LocalDateTime.class))).thenReturn(1, 0);
        when(tokenRepository.findByToken("unknown-" + tokenString)).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(tokenRepository.findByUserAndExpiryDateAfter(eq(testUser), any(LocalDateTime.class)))
            .thenReturn(new ArrayList<>());
        when(tokenRepository.save(any(MagicLoginToken.class))).thenReturn(testToken);

        magicLoginTokenService.consumeToken(tokenString);
        magicLoginTokenService.consumeToken(tokenString);
        magicLoginTokenService.consumeToken("unknown-" + tokenString);
        testUser.setEnabled(false);
        magicLoginTokenService.consumeToken(tokenString);
        testUser.setEnabled(true);
        MagicLinkResponseDTO generated = magicLoginTokenService.generateMagicLink(1L, null, "admin");

        assertFalse(logAppender.list.isEmpty());
        for (ILoggingEvent event : logAppender.list) {
            String message = event.getFormattedMessage();
            assertFalse(message.contains(tokenString), message);
            assertFalse(message.contains(prefix), message);
            assertFalse(message.contains(generated.getToken()), message);
            assertFalse(message.contains(generated.getToken().substring(0, 8)), message);
        }
    }

    @Test
    void testInvalidateAllTokensForUser() {
        magicLoginTokenService.invalidateAllTokensForUser(testUser);

        verify(tokenRepository).deleteByUser(testUser);
    }
}
