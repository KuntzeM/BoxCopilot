package com.boxcopilot.backend.dto;

/**
 * Request DTO for creating a magic login link
 */
public class CreateMagicLinkRequestDTO {
    
    private Integer expiresInHours;
    
    public CreateMagicLinkRequestDTO() {
    }
    
    public CreateMagicLinkRequestDTO(Integer expiresInHours) {
        this.expiresInHours = expiresInHours;
    }
    
    public Integer getExpiresInHours() {
        return expiresInHours;
    }
    
    public void setExpiresInHours(Integer expiresInHours) {
        this.expiresInHours = expiresInHours;
    }
}
