package com.boxcopilot.backend.config;

import com.boxcopilot.backend.service.MagicLoginTokenService;
import com.boxcopilot.backend.service.UserService;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration to resolve circular dependency between UserService and MagicLoginTokenService
 */
@Configuration
public class ServiceConfiguration {
    
    private final UserService userService;
    private final MagicLoginTokenService magicLoginTokenService;
    
    public ServiceConfiguration(UserService userService, MagicLoginTokenService magicLoginTokenService) {
        this.userService = userService;
        this.magicLoginTokenService = magicLoginTokenService;
    }
    
    @PostConstruct
    public void setupDependencies() {
        userService.setMagicLoginTokenService(magicLoginTokenService);
    }
}
