package com.boxcopilot.backend.repository;

import com.boxcopilot.backend.domain.AuthProvider;
import com.boxcopilot.backend.domain.Role;
import com.boxcopilot.backend.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    Optional<User> findByUsername(String username);
    
    Optional<User> findByOidcSubject(String oidcSubject);
    
    boolean existsByUsername(String username);
    
    List<User> findByRole(Role role);
    
    List<User> findByAuthProvider(AuthProvider authProvider);
    
    List<User> findByEnabled(Boolean enabled);
    
    List<User> findByRoleAndAuthProvider(Role role, AuthProvider authProvider);
    
    List<User> findByRoleAndEnabled(Role role, Boolean enabled);
    
    List<User> findByAuthProviderAndEnabled(AuthProvider authProvider, Boolean enabled);
    
    List<User> findByRoleAndAuthProviderAndEnabled(Role role, AuthProvider authProvider, Boolean enabled);
    
    long count();

    /**
     * Atomically records one failed login. Does nothing while the account is locked; a lapsed lock starts a fresh
     * count at 1; reaching {@code threshold} sets {@code lockedUntil}. One statement, so parallel failures cannot
     * lose increments.
     *
     * @return rows changed (0 = unknown or currently locked user)
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE User u SET
              u.failedLoginAttempts = CASE WHEN u.lockedUntil IS NOT NULL THEN 1 ELSE u.failedLoginAttempts + 1 END,
              u.lockedUntil = CASE WHEN u.lockedUntil IS NULL AND u.failedLoginAttempts + 1 >= :threshold
                                   THEN cast(:lockUntil as LocalDateTime) ELSE NULL END
            WHERE u.username = :username AND (u.lockedUntil IS NULL OR u.lockedUntil <= :now)
            """)
    int recordFailedLogin(@Param("username") String username, @Param("now") LocalDateTime now,
                          @Param("lockUntil") LocalDateTime lockUntil, @Param("threshold") int threshold);
}
