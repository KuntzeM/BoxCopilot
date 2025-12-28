package com.boxcopilot.backend.controller;

import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.dto.UserPrincipalDTO;
import com.boxcopilot.backend.service.CustomUserDetailsService;
import com.boxcopilot.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    
    private final UserRepository userRepository;
    
    public AuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Get current authenticated user information and CSRF token
     * Returns 401 Unauthorized if user is not authenticated
     */
    @GetMapping("/me")
    public ResponseEntity<UserPrincipalDTO> getCurrentUser(
            @AuthenticationPrincipal Object principal,
            HttpServletRequest request) {
        
        if (principal == null) {
            log.debug("Unauthenticated user accessing /me endpoint - returning 401");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Get CSRF token
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        String csrfTokenValue = csrfToken != null ? csrfToken.getToken() : null;
        
        UserPrincipalDTO dto;
        
        // Handle OIDC user (Nextcloud)
        if (principal instanceof OidcUser) {
            OidcUser oidcUser = (OidcUser) principal;
            Map<String, Object> claims = oidcUser.getClaims();
            
            String sub = oidcUser.getSubject();
            String email = (String) claims.get("email");
            String name = (String) claims.get("name");
            String preferredUsername = (String) claims.get("preferred_username");
            
            // Find user in database to get role and other details
            Optional<User> userOpt = userRepository.findByOidcSubject(sub);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                dto = new UserPrincipalDTO(
                    user.getId(),
                    user.getUsername(),
                    user.getName(),
                    user.getRole(),
                    user.getAuthProvider(),
                    csrfTokenValue,
                    true
                );
            } else {
                // Fallback to OIDC claims if user not found
                dto = new UserPrincipalDTO(sub, email, name, preferredUsername, csrfTokenValue, true);
                dto.setAdmin(false);
            }
            
            log.info("OIDC user {} accessed /me endpoint", preferredUsername);
        }
        // Handle local user (form login)
        else if (principal instanceof UserDetails) {
            if (principal instanceof CustomUserDetailsService.CustomUserPrincipal) {
                CustomUserDetailsService.CustomUserPrincipal customPrincipal = 
                    (CustomUserDetailsService.CustomUserPrincipal) principal;
                User user = customPrincipal.getUser();
                
                dto = new UserPrincipalDTO(
                    user.getId(),
                    user.getUsername(),
                    user.getName(),
                    user.getRole(),
                    user.getAuthProvider(),
                    csrfTokenValue,
                    true
                );
                
                log.info("Local user {} accessed /me endpoint", user.getUsername());
            } else {
                UserDetails userDetails = (UserDetails) principal;
                dto = new UserPrincipalDTO();
                dto.setUsername(userDetails.getUsername());
                dto.setCsrfToken(csrfTokenValue);
                dto.setAuthenticated(true);
                dto.setAdmin(false);
                
                log.info("User {} accessed /me endpoint", userDetails.getUsername());
            }
        }
        else {
            log.warn("Unknown principal type: {}", principal.getClass().getName());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(dto);
    }

    /**
     * Get CSRF token
     * This endpoint is useful for getting the CSRF token before making authenticated requests
     */
    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> getCsrfToken(HttpServletRequest request) {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            return ResponseEntity.ok(Map.of(
                "token", csrfToken.getToken(),
                "headerName", csrfToken.getHeaderName(),
                "parameterName", csrfToken.getParameterName()
            ));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("error", "CSRF token not available"));
    }
}
