package com.boxcopilot.backend.repository;

import com.boxcopilot.backend.domain.MagicLoginToken;
import com.boxcopilot.backend.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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

    Optional<MagicLoginToken> findTopByUserOrderByCreatedAtDesc(User user);

    /**
     * Atomically marks an unused, unexpired token as used.
     *
     * @return the number of affected rows; 1 means this caller won the redemption, 0 means the token
     *         is unknown, already used or expired
     */
    @Transactional
    @Modifying
    @Query("UPDATE MagicLoginToken t SET t.used = true WHERE t.token = :token AND t.used = false AND t.expiryDate > :now")
    int consume(@Param("token") String token, @Param("now") LocalDateTime now);
}
