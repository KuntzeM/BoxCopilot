package com.boxcopilot.backend.config;

import com.boxcopilot.backend.service.CustomOidcUserService;
import com.boxcopilot.backend.service.MagicLoginTokenService;
import com.boxcopilot.backend.service.UserService;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Configuration to resolve circular dependencies between services and security config.
 * 
 * Dependency cycles resolved:
 * 1. UserService ↔ MagicLoginTokenService: UserService.magicLoginTokenService set via setter
 * 2. OAuth2SecurityConfig ↔ CustomOidcUserService ↔ UserService: 
 *    OAuth2SecurityConfig.customOidcUserService set via setter
 */
@Configuration
@Profile({"dev", "prod"})
public class ServiceConfiguration {
    
    private final UserService userService;
    private final MagicLoginTokenService magicLoginTokenService;
    private final CustomOidcUserService customOidcUserService;
    private final OAuth2SecurityConfig oAuth2SecurityConfig;
    
    public ServiceConfiguration(UserService userService, 
                               MagicLoginTokenService magicLoginTokenService,
                               CustomOidcUserService customOidcUserService,
                               OAuth2SecurityConfig oAuth2SecurityConfig) {
        this.userService = userService;
        this.magicLoginTokenService = magicLoginTokenService;
        this.customOidcUserService = customOidcUserService;
        this.oAuth2SecurityConfig = oAuth2SecurityConfig;
    }
    
    @PostConstruct
    public void setupDependencies() {
        // Resolve UserService ↔ MagicLoginTokenService cycle
        userService.setMagicLoginTokenService(magicLoginTokenService);
        
        // Resolve OAuth2SecurityConfig ↔ CustomOidcUserService ↔ UserService cycle
        oAuth2SecurityConfig.setCustomOidcUserService(customOidcUserService);
    }
}

