package com.boxcopilot.backend.dto;

import com.boxcopilot.backend.domain.Role;

/**
 * DTO for updating existing users
 */
public class UpdateUserDTO {
    
    private String username;
    private String name;
    private Role role;
    private Boolean enabled;
    
    public UpdateUserDTO() {
    }
    
    public UpdateUserDTO(String username, String name, Role role, Boolean enabled) {
        this.username = username;
        this.name = name;
        this.role = role;
        this.enabled = enabled;
    }
    
    // Getters and setters
    
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
}
