package com.boxcopilot.backend.dto;

import com.boxcopilot.backend.domain.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO for creating new local users
 */
public class CreateUserDTO {
    
    @NotBlank(message = "Username is required")
    @Size(max = 255, message = "Username must be at most 255 characters")
    private String username;
    
    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name must be at most 255 characters")
    private String name;
    
    // Password is optional - can be null for passwordless accounts
    @Size(min = 12, max = 128, message = "Password must be between 12 and 128 characters")
    private String password;
    
    @NotNull(message = "Role is required")
    private Role role;
    
    private Boolean enabled = true;
    
    public CreateUserDTO() {
    }
    
    public CreateUserDTO(String username, String name, String password, Role role) {
        this.username = username;
        this.name = name;
        this.password = password;
        this.role = role;
        this.enabled = true;
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
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        // The admin form sends "" for a passwordless account; treat blank as absent so @Size does not reject it.
        this.password = (password == null || password.isBlank()) ? null : password;
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
