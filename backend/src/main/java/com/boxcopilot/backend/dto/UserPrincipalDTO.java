package com.boxcopilot.backend.dto;

public class UserPrincipalDTO {
    private String sub;
    private String email;
    private String name;
    private String preferredUsername;
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
}
