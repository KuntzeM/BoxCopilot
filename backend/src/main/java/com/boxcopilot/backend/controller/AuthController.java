package com.boxcopilot.backend.controller;

import com.boxcopilot.backend.dto.UserPrincipalDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    /**
     * Get current authenticated user information and CSRF token
     * Returns 401 Unauthorized if user is not authenticated
     */
    @GetMapping("/me")
    public ResponseEntity<UserPrincipalDTO> getCurrentUser(
            @AuthenticationPrincipal OidcUser oidcUser,
            HttpServletRequest request) {
        
        if (oidcUser == null) {
            log.debug("Unauthenticated user accessing /me endpoint - returning 401");
            // Return 401 Unauthorized for unauthenticated requests
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Extract OIDC claims
        Map<String, Object> claims = oidcUser.getClaims();
        
        String sub = oidcUser.getSubject();
        String email = (String) claims.get("email");
        String name = (String) claims.get("name");
        String preferredUsername = (String) claims.get("preferred_username");

        // Get CSRF token
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        String csrfTokenValue = csrfToken != null ? csrfToken.getToken() : null;

        UserPrincipalDTO dto = new UserPrincipalDTO(
            sub,
            email,
            name,
            preferredUsername,
            csrfTokenValue,
            true
        );

        log.info("User {} ({}) accessed /me endpoint", preferredUsername, email);
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
