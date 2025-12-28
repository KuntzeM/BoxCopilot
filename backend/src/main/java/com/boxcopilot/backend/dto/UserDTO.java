package com.boxcopilot.backend.dto;

import com.boxcopilot.backend.domain.AuthProvider;
import com.boxcopilot.backend.domain.Role;

import java.time.LocalDateTime;

/**
 * DTO for user responses
 */
public class UserDTO {
    private Long id;
    private String username;
    private String name;
    private AuthProvider authProvider;
    private Role role;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime lastLogin;
    private Integer failedLoginAttempts;
    private LocalDateTime lockedUntil;
    private Boolean hasPassword;
    
    public UserDTO() {
    }
    
    public UserDTO(Long id, String username, String name, AuthProvider authProvider, Role role, 
                   Boolean enabled, LocalDateTime createdAt, LocalDateTime lastLogin, 
                   Integer failedLoginAttempts, LocalDateTime lockedUntil) {
        this.id = id;
        this.username = username;
        this.name = name;
        this.authProvider = authProvider;
        this.role = role;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.lastLogin = lastLogin;
        this.failedLoginAttempts = failedLoginAttempts;
        this.lockedUntil = lockedUntil;
    }
    
    public UserDTO(Long id, String username, String name, AuthProvider authProvider, Role role, 
                   Boolean enabled, LocalDateTime createdAt, LocalDateTime lastLogin, 
                   Integer failedLoginAttempts, LocalDateTime lockedUntil, Boolean hasPassword) {
        this.id = id;
        this.username = username;
        this.name = name;
        this.authProvider = authProvider;
        this.role = role;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.lastLogin = lastLogin;
        this.failedLoginAttempts = failedLoginAttempts;
        this.lockedUntil = lockedUntil;
        this.hasPassword = hasPassword;
    }
    
    // Getters and setters
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public AuthProvider getAuthProvider() {
        return authProvider;
    }
    
    public void setAuthProvider(AuthProvider authProvider) {
        this.authProvider = authProvider;
    }
    
    public Role getRole() {
        return role;
    }
    
    public void setRole(Role role) {
        this.role = role;
    }
    
    public Boolean getEnabled() {
        return enabled;
    }
    
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getLastLogin() {
        return lastLogin;
    }
    
    public void setLastLogin(LocalDateTime lastLogin) {
        this.lastLogin = lastLogin;
    }
    
    public Integer getFailedLoginAttempts() {
        return failedLoginAttempts;
    }
    
    public void setFailedLoginAttempts(Integer failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }
    
    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }
    
    public void setLockedUntil(LocalDateTime lockedUntil) {
        this.lockedUntil = lockedUntil;
    }
    
    public Boolean getHasPassword() {
        return hasPassword;
    }
    
    public void setHasPassword(Boolean hasPassword) {
        this.hasPassword = hasPassword;
    }
}
