package com.boxcopilot.backend.repository;

import com.boxcopilot.backend.domain.AuthProvider;
import com.boxcopilot.backend.domain.Role;
import com.boxcopilot.backend.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
