package com.boxcopilot.backend.domain;

/**
 * Authentication provider types
 */
public enum AuthProvider {
    /**
     * Authentication via Nextcloud OIDC
     */
    NEXTCLOUD,
    
    /**
     * Local username/password authentication
     */
    LOCAL
}
