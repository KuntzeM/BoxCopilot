package com.boxcopilot.backend.dto;

import java.time.LocalDateTime;

/**
 * Response DTO for magic login link creation
 */
public class MagicLinkResponseDTO {
    
    private String token;
    private String url;
    private LocalDateTime expiresAt;
    private Boolean used;
    
    public MagicLinkResponseDTO() {
    }
    
    public MagicLinkResponseDTO(String token, String url, LocalDateTime expiresAt) {
        this.token = token;
        this.url = url;
        this.expiresAt = expiresAt;
        this.used = false;
    }
    
    public String getToken() {
        return token;
    }
    
    public void setToken(String token) {
        this.token = token;
    }
    
    public String getUrl() {
        return url;
    }
    
    public void setUrl(String url) {
        this.url = url;
    }
    
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
    
    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Boolean getUsed() {
        return used;
    }

    public void setUsed(Boolean used) {
        this.used = used;
    }
}
