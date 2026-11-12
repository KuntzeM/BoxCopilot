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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class MagicLoginTokenService {
    
    private static final Logger log = LoggerFactory.getLogger(MagicLoginTokenService.class);
    
    static final int MAX_VALID_HOURS = 72;

    private static final String MAGIC_LINK_PATH = "/magic#t=";

    private final MagicLoginTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final String frontendBaseUrl;
    private final int defaultValidHours;

    public MagicLoginTokenService(MagicLoginTokenRepository tokenRepository,
                                  UserRepository userRepository,
                                  @Value("${frontend.url}") String frontendUrl,
                                  @Value("${app.magiclink.default-valid-hours:24}") int defaultValidHours) {
        if (!StringUtils.hasText(frontendUrl)) {
            throw new IllegalStateException("frontend.url (FRONTEND_URL) must be configured to build magic links");
        }
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.frontendBaseUrl = frontendUrl.endsWith("/")
                ? frontendUrl.substring(0, frontendUrl.length() - 1)
                : frontendUrl;
        this.defaultValidHours = defaultValidHours;
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
        if (validHours < 1 || validHours > MAX_VALID_HOURS) {
            throw new IllegalArgumentException("Magic link validity must be between 1 and " + MAX_VALID_HOURS + " hours");
        }
        
        // Invalidate any existing active tokens for this user
        invalidateActiveTokensForUser(user);
        
        // Generate new token
        String tokenString = UUID.randomUUID().toString();
        MagicLoginToken token = new MagicLoginToken(tokenString, user, validHours);
        MagicLoginToken savedToken = tokenRepository.save(token);
        
        String magicLoginUrl = frontendBaseUrl + MAGIC_LINK_PATH + tokenString;

        log.info("Admin '{}' generated magic login link for user '{}' (id: {}), token id: {}, expires at: {}", 
                adminUsername, user.getUsername(), userId, savedToken.getId(), savedToken.getExpiryDate());
        
        return new MagicLinkResponseDTO(tokenString, magicLoginUrl, savedToken.getExpiryDate());
    }

    /**
     * Redeem a magic login token and return the associated user.
     * The user is checked before the token is consumed, so a disabled or locked user never burns the token.
     * Consumption is a single conditional UPDATE, so concurrent redemptions succeed at most once.
     */
    @Transactional
    public Optional<User> consumeToken(String tokenString) {
        Optional<MagicLoginToken> tokenOpt = tokenRepository.findByToken(tokenString);
        if (tokenOpt.isEmpty()) {
            log.warn("Magic login rejected: unknown token");
            return Optional.empty();
        }

        MagicLoginToken token = tokenOpt.get();
        User user = token.getUser();

        if (!Boolean.TRUE.equals(user.getEnabled())) {
            log.warn("Magic login rejected: user '{}' is disabled", user.getUsername());
            return Optional.empty();
        }

        if (tokenRepository.consume(tokenString, LocalDateTime.now()) != 1) {
            log.warn("Magic login rejected: token of user '{}' is used or expired", user.getUsername());
            return Optional.empty();
        }

        log.info("Magic login token redeemed for user '{}' (id: {})", user.getUsername(), user.getId());
        return Optional.of(user);
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
     * Invalidate all tokens for a user by user ID
     */
    @Transactional
    public void invalidateAllTokensForUserId(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        invalidateAllTokensForUser(user);
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
