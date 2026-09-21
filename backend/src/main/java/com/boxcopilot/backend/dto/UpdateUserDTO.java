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
    private Boolean mcpAllowed;

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

    /**
     * Admin-only switch; {@code null} leaves the current value unchanged.
     */
    public Boolean getMcpAllowed() {
        return mcpAllowed;
    }

    public void setMcpAllowed(Boolean mcpAllowed) {
        this.mcpAllowed = mcpAllowed;
    }
}
