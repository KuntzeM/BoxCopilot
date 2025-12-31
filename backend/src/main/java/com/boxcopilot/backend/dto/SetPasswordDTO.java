package com.boxcopilot.backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO for setting user password
 */
public class SetPasswordDTO {
    
    @NotBlank(message = "Password is required")
    private String password;
    
    public SetPasswordDTO() {
    }
    
    public SetPasswordDTO(String password) {
        this.password = password;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
}
