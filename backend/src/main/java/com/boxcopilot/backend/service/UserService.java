package com.boxcopilot.backend.service;

import com.boxcopilot.backend.domain.AuthProvider;
import com.boxcopilot.backend.domain.Role;
import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.dto.CreateUserDTO;
import com.boxcopilot.backend.dto.UpdateUserDTO;
import com.boxcopilot.backend.dto.UserDTO;
import com.boxcopilot.backend.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UserService {
    
    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    @Value("${app.admin.username:admin}")
    private String defaultAdminUsername;
    
    @Value("${app.admin.password:admin}")
    private String defaultAdminPassword;
    
    @Value("${app.admin.name:Administrator}")
    private String defaultAdminName;
    
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }
    
    /**
     * Create default admin account on startup if no users exist
     */
    @PostConstruct
    public void initializeDefaultAdmin() {
        if (userRepository.count() == 0) {
            log.info("No users found. Creating default admin account...");
            User admin = new User(defaultAdminUsername, defaultAdminName, AuthProvider.LOCAL, Role.ADMIN);
            admin.setPasswordHash(passwordEncoder.encode(defaultAdminPassword));
            admin.setEnabled(true);
            userRepository.save(admin);
            log.info("Default admin account created with username: {}", defaultAdminUsername);
        }
    }
    
    /**
     * Find user by username
     */
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }
    
    /**
     * Find user by OIDC subject
     */
    public Optional<User> findByOidcSubject(String oidcSubject) {
        return userRepository.findByOidcSubject(oidcSubject);
    }
    
    /**
     * Get all users with optional filtering
     */
    public List<UserDTO> getAllUsers(Role role, AuthProvider authProvider, Boolean enabled) {
        List<User> users;
        
        if (role != null && authProvider != null && enabled != null) {
            users = userRepository.findByRoleAndAuthProviderAndEnabled(role, authProvider, enabled);
        } else if (role != null && authProvider != null) {
            users = userRepository.findByRoleAndAuthProvider(role, authProvider);
        } else if (role != null && enabled != null) {
            users = userRepository.findByRoleAndEnabled(role, enabled);
        } else if (authProvider != null && enabled != null) {
            users = userRepository.findByAuthProviderAndEnabled(authProvider, enabled);
        } else if (role != null) {
            users = userRepository.findByRole(role);
        } else if (authProvider != null) {
            users = userRepository.findByAuthProvider(authProvider);
        } else if (enabled != null) {
            users = userRepository.findByEnabled(enabled);
        } else {
            users = userRepository.findAll();
        }
        
        return users.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    /**
     * Get user by ID
     */
    public UserDTO getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        return convertToDTO(user);
    }
    
    /**
     * Create new local user
     */
    @Transactional
    public UserDTO createUser(CreateUserDTO createUserDTO) {
        // Check if username already exists
        if (userRepository.existsByUsername(createUserDTO.getUsername())) {
            throw new IllegalArgumentException("Username already exists: " + createUserDTO.getUsername());
        }
        
        User user = new User(
            createUserDTO.getUsername(),
            createUserDTO.getName(),
            AuthProvider.LOCAL,
            createUserDTO.getRole()
        );
        user.setPasswordHash(passwordEncoder.encode(createUserDTO.getPassword()));
        user.setEnabled(createUserDTO.getEnabled());
        
        User savedUser = userRepository.save(user);
        log.info("Created new local user: {}", savedUser.getUsername());
        
        return convertToDTO(savedUser);
    }
    
    /**
     * Update existing user
     */
    @Transactional
    public UserDTO updateUser(Long id, UpdateUserDTO updateUserDTO) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        
        // Update username if provided and different (check for conflicts)
        if (updateUserDTO.getUsername() != null && !updateUserDTO.getUsername().equals(user.getUsername())) {
            if (user.getAuthProvider() == AuthProvider.NEXTCLOUD) {
                throw new IllegalArgumentException("Cannot change username for Nextcloud users");
            }
            if (userRepository.existsByUsername(updateUserDTO.getUsername())) {
                throw new IllegalArgumentException("Username already exists: " + updateUserDTO.getUsername());
            }
            user.setUsername(updateUserDTO.getUsername());
        }
        
        if (updateUserDTO.getName() != null) {
            user.setName(updateUserDTO.getName());
        }
        
        if (updateUserDTO.getRole() != null) {
            user.setRole(updateUserDTO.getRole());
        }
        
        if (updateUserDTO.getEnabled() != null) {
            user.setEnabled(updateUserDTO.getEnabled());
        }
        
        User updatedUser = userRepository.save(user);
        log.info("Updated user: {}", updatedUser.getUsername());
        
        return convertToDTO(updatedUser);
    }
    
    /**
     * Set user password (admin only)
     */
    @Transactional
    public void setPassword(Long id, String password) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        
        if (user.getAuthProvider() != AuthProvider.LOCAL) {
            throw new IllegalArgumentException("Cannot set password for non-local users");
        }
        
        user.setPasswordHash(passwordEncoder.encode(password));
        userRepository.save(user);
        log.info("Password updated for user: {}", user.getUsername());
    }
    
    /**
     * Unlock user account
     */
    @Transactional
    public void unlockUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        
        user.unlock();
        userRepository.save(user);
        log.info("Unlocked user account: {}", user.getUsername());
    }
    
    /**
     * Delete user
     */
    @Transactional
    public void deleteUser(Long id, String currentUsername) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        
        if (user.getUsername().equals(currentUsername)) {
            throw new IllegalArgumentException("Cannot delete currently logged-in user");
        }
        
        userRepository.delete(user);
        log.info("Deleted user: {}", user.getUsername());
    }
    
    /**
     * Create or update Nextcloud user on OIDC login
     */
    @Transactional
    public User createOrUpdateOidcUser(String oidcSubject, String preferredUsername, String name, String email) {
        // Try to find existing user by OIDC subject
        Optional<User> existingUser = userRepository.findByOidcSubject(oidcSubject);
        
        if (existingUser.isPresent()) {
            // Update last login
            User user = existingUser.get();
            user.setLastLogin(LocalDateTime.now());
            user.resetFailedAttempts();
            log.info("Existing Nextcloud user logged in: {}", user.getUsername());
            return userRepository.save(user);
        }
        
        // Create new user with unique username
        String username = resolveUsernameConflict(preferredUsername);
        User newUser = new User(username, name, AuthProvider.NEXTCLOUD, Role.USER);
        newUser.setOidcSubject(oidcSubject);
        newUser.setEnabled(true);
        newUser.setLastLogin(LocalDateTime.now());
        
        User savedUser = userRepository.save(newUser);
        log.info("Created new Nextcloud user: {} (OIDC subject: {})", savedUser.getUsername(), oidcSubject);
        
        return savedUser;
    }
    
    /**
     * Resolve username conflicts by adding suffix
     */
    private String resolveUsernameConflict(String baseUsername) {
        String username = baseUsername;
        int suffix = 0;
        while (userRepository.existsByUsername(username)) {
            suffix++;
            username = baseUsername + "_nc" + (suffix > 1 ? suffix : "");
        }
        return username;
    }
    
    /**
     * Update last login timestamp
     */
    @Transactional
    public void updateLastLogin(User user) {
        user.setLastLogin(LocalDateTime.now());
        user.resetFailedAttempts();
        userRepository.save(user);
    }
    
    /**
     * Record failed login attempt
     */
    @Transactional
    public void recordFailedLogin(String username) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.incrementFailedAttempts();
            userRepository.save(user);
            log.warn("Failed login attempt for user: {} (attempts: {})", username, user.getFailedLoginAttempts());
        }
    }
    
    /**
     * Convert User entity to DTO
     */
    private UserDTO convertToDTO(User user) {
        return new UserDTO(
            user.getId(),
            user.getUsername(),
            user.getName(),
            user.getAuthProvider(),
            user.getRole(),
            user.getEnabled(),
            user.getCreatedAt(),
            user.getLastLogin(),
            user.getFailedLoginAttempts(),
            user.getLockedUntil()
        );
    }
}
