package com.boxcopilot.backend.dto;

import com.boxcopilot.backend.domain.AuthProvider;
import com.boxcopilot.backend.domain.Role;

public class UserPrincipalDTO {
    private Long id;
    private String sub;
    private String email;
    private String name;
    private String preferredUsername;
    private String username;
    private Role role;
    private AuthProvider authProvider;
    private boolean isAdmin;
    private String csrfToken;
    private boolean authenticated;

    public UserPrincipalDTO() {
    }

    public UserPrincipalDTO(String sub, String email, String name, String preferredUsername, 
                           String csrfToken, boolean authenticated) {
        this.sub = sub;
        this.email = email;
        this.name = name;
        this.preferredUsername = preferredUsername;
        this.csrfToken = csrfToken;
        this.authenticated = authenticated;
    }
    
    public UserPrincipalDTO(Long id, String username, String name, Role role, AuthProvider authProvider,
                           String csrfToken, boolean authenticated) {
        this.id = id;
        this.username = username;
        this.name = name;
        this.role = role;
        this.authProvider = authProvider;
        this.isAdmin = (role == Role.ADMIN);
        this.csrfToken = csrfToken;
        this.authenticated = authenticated;
    }

    // Getters and Setters
    public String getSub() {
        return sub;
    }

    public void setSub(String sub) {
        this.sub = sub;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPreferredUsername() {
        return preferredUsername;
    }

    public void setPreferredUsername(String preferredUsername) {
        this.preferredUsername = preferredUsername;
    }

    public String getCsrfToken() {
        return csrfToken;
    }

    public void setCsrfToken(String csrfToken) {
        this.csrfToken = csrfToken;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }
    
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
    
    public Role getRole() {
        return role;
    }
    
    public void setRole(Role role) {
        this.role = role;
        this.isAdmin = (role == Role.ADMIN);
    }
    
    public AuthProvider getAuthProvider() {
        return authProvider;
    }
    
    public void setAuthProvider(AuthProvider authProvider) {
        this.authProvider = authProvider;
    }
    
    public boolean isAdmin() {
        return isAdmin;
    }
    
    public void setAdmin(boolean admin) {
        isAdmin = admin;
    }
}
