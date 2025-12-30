package com.boxcopilot.backend.service;

import com.boxcopilot.backend.domain.AuthProvider;
import com.boxcopilot.backend.domain.MagicLoginToken;
import com.boxcopilot.backend.domain.Role;
import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.dto.CreateMagicLinkRequestDTO;
import com.boxcopilot.backend.dto.MagicLinkResponseDTO;
import com.boxcopilot.backend.repository.MagicLoginTokenRepository;
import com.boxcopilot.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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

    @Mock
    private MagicLoginTokenRepository tokenRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MagicLoginTokenService magicLoginTokenService;

    private User testUser;
    private MagicLoginToken testToken;

    @BeforeEach
    void setUp() {
        // Set up service with default configuration
        ReflectionTestUtils.setField(magicLoginTokenService, "defaultValidHours", 24);
        ReflectionTestUtils.setField(magicLoginTokenService, "backendBaseUrl", "http://localhost:8080");
        
        // Create test user
        testUser = new User("testuser", "Test User", AuthProvider.LOCAL, Role.USER);
        testUser.setId(1L);
        testUser.setEnabled(true);
        
        // Create test token
        testToken = new MagicLoginToken(UUID.randomUUID().toString(), testUser, 24);
        testToken.setId(1L);
    }

    @Test
    void testGenerateMagicLink_Success() {
        // Arrange
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(tokenRepository.findByUserAndExpiryDateAfter(eq(testUser), any(LocalDateTime.class)))
            .thenReturn(new ArrayList<>());
        when(tokenRepository.save(any(MagicLoginToken.class))).thenReturn(testToken);

        // Act
        MagicLinkResponseDTO result = magicLoginTokenService.generateMagicLink(
            1L, new CreateMagicLinkRequestDTO(), "admin"
        );

        // Assert
        assertNotNull(result);
        assertNotNull(result.getToken());
        assertNotNull(result.getUrl());
        assertNotNull(result.getExpiresAt());
        assertTrue(result.getUrl().contains("/api/v1/auth/magic-login?token="));
        verify(tokenRepository).save(any(MagicLoginToken.class));
    }

    @Test
    void testGenerateMagicLink_WithCustomExpiry() {
        // Arrange
        CreateMagicLinkRequestDTO request = new CreateMagicLinkRequestDTO(48);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(tokenRepository.findByUserAndExpiryDateAfter(eq(testUser), any(LocalDateTime.class)))
            .thenReturn(new ArrayList<>());
        
        MagicLoginToken tokenWith48Hours = new MagicLoginToken(UUID.randomUUID().toString(), testUser, 48);
        when(tokenRepository.save(any(MagicLoginToken.class))).thenReturn(tokenWith48Hours);

        // Act
        MagicLinkResponseDTO result = magicLoginTokenService.generateMagicLink(
            1L, request, "admin"
        );

        // Assert
        assertNotNull(result);
        verify(tokenRepository).save(any(MagicLoginToken.class));
    }

    @Test
    void testGenerateMagicLink_InvalidatesOldTokens() {
        // Arrange
        List<MagicLoginToken> existingTokens = List.of(testToken);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(tokenRepository.findByUserAndExpiryDateAfter(eq(testUser), any(LocalDateTime.class)))
            .thenReturn(existingTokens);
        when(tokenRepository.save(any(MagicLoginToken.class))).thenReturn(testToken);

        // Act
        magicLoginTokenService.generateMagicLink(1L, new CreateMagicLinkRequestDTO(), "admin");

        // Assert
        verify(tokenRepository).deleteAll(existingTokens);
    }

    @Test
    void testValidateToken_Success() {
        // Arrange
        String tokenString = testToken.getToken();
        when(tokenRepository.findByToken(tokenString)).thenReturn(Optional.of(testToken));
        when(tokenRepository.save(any(MagicLoginToken.class))).thenReturn(testToken);

        // Act
        Optional<User> result = magicLoginTokenService.validateToken(tokenString);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(testUser, result.get());
        verify(tokenRepository).save(testToken);
    }

    @Test
    void testValidateToken_TokenNotFound() {
        // Arrange
        String tokenString = "nonexistent-token";
        when(tokenRepository.findByToken(tokenString)).thenReturn(Optional.empty());

        // Act
        Optional<User> result = magicLoginTokenService.validateToken(tokenString);

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    void testValidateToken_ExpiredToken() {
        // Arrange
        MagicLoginToken expiredToken = new MagicLoginToken(UUID.randomUUID().toString(), testUser, 24);
        expiredToken.setExpiryDate(LocalDateTime.now().minusHours(1)); // Expired 1 hour ago
        when(tokenRepository.findByToken(anyString())).thenReturn(Optional.of(expiredToken));

        // Act
        Optional<User> result = magicLoginTokenService.validateToken(expiredToken.getToken());

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    void testValidateToken_DisabledUser() {
        // Arrange
        testUser.setEnabled(false);
        when(tokenRepository.findByToken(anyString())).thenReturn(Optional.of(testToken));

        // Act
        Optional<User> result = magicLoginTokenService.validateToken(testToken.getToken());

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    void testInvalidateAllTokensForUser() {
        // Act
        magicLoginTokenService.invalidateAllTokensForUser(testUser);

        // Assert
        verify(tokenRepository).deleteByUser(testUser);
    }
}
