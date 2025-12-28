package com.boxcopilot.backend.repository;

import com.boxcopilot.backend.domain.MagicLoginToken;
import com.boxcopilot.backend.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MagicLoginTokenRepository extends JpaRepository<MagicLoginToken, Long> {
    
    /**
     * Find token by token string
     */
    Optional<MagicLoginToken> findByToken(String token);
    
    /**
     * Find all active (non-expired) tokens for a user
     */
    List<MagicLoginToken> findByUserAndExpiryDateAfter(User user, LocalDateTime date);
    
    /**
     * Find all tokens for a user
     */
    List<MagicLoginToken> findByUser(User user);
    
    /**
     * Delete all tokens for a user
     */
    void deleteByUser(User user);
    
    /**
     * Delete expired tokens
     */
    void deleteByExpiryDateBefore(LocalDateTime date);
}
