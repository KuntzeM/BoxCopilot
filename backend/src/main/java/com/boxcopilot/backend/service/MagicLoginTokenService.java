package com.boxcopilot.backend.service;

import com.boxcopilot.backend.domain.MagicLoginToken;
import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.dto.CreateMagicLinkRequestDTO;
import com.boxcopilot.backend.dto.MagicLinkResponseDTO;
import com.boxcopilot.backend.repository.MagicLoginTokenRepository;
import com.boxcopilot.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class MagicLoginTokenService {
    
    private static final Logger log = LoggerFactory.getLogger(MagicLoginTokenService.class);
    
    private final MagicLoginTokenRepository tokenRepository;
    private final UserRepository userRepository;
    
    @Value("${app.magiclink.default-valid-hours:24}")
    private int defaultValidHours;
    
    @Value("${app.magiclink.backend-base-url:}")
    private String backendBaseUrl;
    
    public MagicLoginTokenService(MagicLoginTokenRepository tokenRepository, UserRepository userRepository) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
    }
    
    /**
     * Generate a magic login link for a user
     * Invalidates any existing active tokens for the user
     */
    @Transactional
    public MagicLinkResponseDTO generateMagicLink(Long userId, CreateMagicLinkRequestDTO request, String adminUsername) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        
        int validHours = (request != null && request.getExpiresInHours() != null) 
                ? request.getExpiresInHours() 
                : defaultValidHours;
        
        // Invalidate any existing active tokens for this user
        invalidateActiveTokensForUser(user);
        
        // Generate new token
        String tokenString = UUID.randomUUID().toString();
        MagicLoginToken token = new MagicLoginToken(tokenString, user, validHours);
        MagicLoginToken savedToken = tokenRepository.save(token);
        
        // Build the magic login URL against backend, so the session can be created server-side
        String magicLoginUrl = UriComponentsBuilder
            .fromUriString(resolveBaseUrl())
            .path("/api/v1/auth/magic-login")
            .queryParam("token", tokenString)
            .build()
            .toUriString();
        
        log.info("Admin '{}' generated magic login link for user '{}' (id: {}), token id: {}, expires at: {}", 
                adminUsername, user.getUsername(), userId, savedToken.getId(), savedToken.getExpiryDate());
        
        return new MagicLinkResponseDTO(tokenString, magicLoginUrl, savedToken.getExpiryDate());
    }

    /**
     * Determine the base URL for constructing the magic login link.
     * Prefers explicit configuration, otherwise derives from the current request context.
     */
    private String resolveBaseUrl() {
        if (StringUtils.hasText(backendBaseUrl)) {
            return backendBaseUrl.endsWith("/")
                    ? backendBaseUrl.substring(0, backendBaseUrl.length() - 1)
                    : backendBaseUrl;
        }

        // Fall back to the current request context (works inside MVC request lifecycle)
        return ServletUriComponentsBuilder.fromCurrentContextPath()
            .build()
            .toUriString();
    }
    
    /**
     * Validate a magic login token and return the associated user
     */
    @Transactional
    public Optional<User> validateToken(String tokenString) {
        log.debug("Validating magic login token: {}", tokenString.substring(0, Math.min(8, tokenString.length())) + "...");
        
        Optional<MagicLoginToken> tokenOpt = tokenRepository.findByToken(tokenString);
        
        if (tokenOpt.isEmpty()) {
            log.warn("Magic login token not found: {}", tokenString.substring(0, Math.min(8, tokenString.length())) + "...");
            return Optional.empty();
        }
        
        MagicLoginToken token = tokenOpt.get();
        
        // Check if token is expired
        if (token.isExpired()) {
            log.warn("Magic login token expired for user '{}': expires at {}", 
                    token.getUser().getUsername(), token.getExpiryDate());
            return Optional.empty();
        }
        
        // Check if user is enabled
        if (!token.getUser().getEnabled()) {
            log.warn("Magic login attempted for disabled user: {}", token.getUser().getUsername());
            return Optional.empty();
        }
        
        // Mark token as used (for audit purposes, but token remains reusable)
        token.markAsUsed();
        tokenRepository.save(token);
        
        log.info("Magic login successful for user '{}' (id: {})", 
                token.getUser().getUsername(), token.getUser().getId());
        
        return Optional.of(token.getUser());
    }
    
    /**
     * Invalidate all active tokens for a user
     */
    @Transactional
    public void invalidateActiveTokensForUser(User user) {
        List<MagicLoginToken> activeTokens = tokenRepository.findByUserAndExpiryDateAfter(user, LocalDateTime.now());
        if (!activeTokens.isEmpty()) {
            tokenRepository.deleteAll(activeTokens);
            log.debug("Invalidated {} active tokens for user '{}'", activeTokens.size(), user.getUsername());
        }
    }
    
    /**
     * Invalidate all tokens for a user (called when user is disabled or deleted)
     */
    @Transactional
    public void invalidateAllTokensForUser(User user) {
        tokenRepository.deleteByUser(user);
        log.info("Invalidated all magic login tokens for user '{}'", user.getUsername());
    }
    
    /**
     * Cleanup expired tokens (can be called periodically)
     */
    @Transactional
    public void cleanupExpiredTokens() {
        tokenRepository.deleteByExpiryDateBefore(LocalDateTime.now());
        log.debug("Cleaned up expired magic login tokens");
    }
}
