package com.boxcopilot.backend.controller;

import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.dto.UserPrincipalDTO;
import com.boxcopilot.backend.service.CustomUserDetailsService;
import com.boxcopilot.backend.service.MagicLoginTokenService;
import com.boxcopilot.backend.service.UserService;
import com.boxcopilot.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    
    private final UserRepository userRepository;
    private final MagicLoginTokenService magicLoginTokenService;
    private final UserService userService;
    
    @Value("${frontend.url}")
    private String frontendUrl;
    
    public AuthController(UserRepository userRepository, 
                         MagicLoginTokenService magicLoginTokenService,
                         UserService userService) {
        this.userRepository = userRepository;
        this.magicLoginTokenService = magicLoginTokenService;
        this.userService = userService;
    }

    /**
     * Get current authenticated user information and CSRF token
     * Returns 401 Unauthorized if user is not authenticated
     */
    @GetMapping("/me")
    public ResponseEntity<UserPrincipalDTO> getCurrentUser(
            @AuthenticationPrincipal Object principal,
            HttpServletRequest request) {
        // Get CSRF token early so we can return it for anonymous users too
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        String csrfTokenValue = csrfToken != null ? csrfToken.getToken() : null;

        // Anonymous / unauthenticated: return a neutral DTO instead of 401
        if (principal == null || principal instanceof String) {
            UserPrincipalDTO dto = new UserPrincipalDTO();
            dto.setAuthenticated(false);
            dto.setAdmin(false);
            dto.setCsrfToken(csrfTokenValue);
            log.debug("Unauthenticated user accessing /me endpoint - returning anonymous principal info");
            return ResponseEntity.ok(dto);
        }

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
    
    /**
     * Magic login endpoint - authenticate via magic link token
     * Public endpoint that creates a session and redirects to frontend
     */
    @GetMapping("/auth/magic-login")
    public RedirectView magicLogin(@RequestParam String token, HttpServletRequest request) {
        log.info("Magic login attempt with token: {}...", token.substring(0, Math.min(8, token.length())));
        
        Optional<User> userOpt = magicLoginTokenService.validateToken(token);
        
        if (userOpt.isEmpty()) {
            log.warn("Invalid or expired magic login token");
            return new RedirectView(frontendUrl + "/?magicLogin=failed&reason=invalid_token");
        }
        
        User user = userOpt.get();
        
        // Create UserDetails for the user
        CustomUserDetailsService.CustomUserPrincipal userPrincipal = 
            new CustomUserDetailsService.CustomUserPrincipal(user);
        
        // Create authentication token
        Authentication authentication = new UsernamePasswordAuthenticationToken(
            userPrincipal,
            null,
            userPrincipal.getAuthorities()
        );
        
        // Set authentication in security context
        SecurityContext securityContext = SecurityContextHolder.getContext();
        securityContext.setAuthentication(authentication);
        
        // Save security context to session
        HttpSession session = request.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        
        // Update last login
        userService.updateLastLogin(user);
        
        log.info("Magic login successful for user '{}', session created", user.getUsername());
        
        return new RedirectView(frontendUrl + "/?magicLogin=success");
    }
}
