package com.boxcopilot.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Request DTO for creating a magic login link
 */
public class CreateMagicLinkRequestDTO {
    
    @Min(1)
    @Max(72)
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
