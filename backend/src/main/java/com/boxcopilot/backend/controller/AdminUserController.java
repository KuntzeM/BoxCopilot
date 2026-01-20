package com.boxcopilot.backend.controller;

import com.boxcopilot.backend.domain.AuthProvider;
import com.boxcopilot.backend.domain.Role;
import com.boxcopilot.backend.dto.CreateMagicLinkRequestDTO;
import com.boxcopilot.backend.dto.CreateUserDTO;
import com.boxcopilot.backend.dto.MagicLinkResponseDTO;
import com.boxcopilot.backend.dto.SetPasswordDTO;
import com.boxcopilot.backend.dto.UpdateUserDTO;
import com.boxcopilot.backend.dto.UserDTO;
import com.boxcopilot.backend.service.MagicLoginTokenService;
import com.boxcopilot.backend.service.UserService;
import com.boxcopilot.backend.service.CustomUserDetailsService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin API for user management
 * All endpoints require ADMIN role
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
    
    private static final Logger log = LoggerFactory.getLogger(AdminUserController.class);
    
    private final UserService userService;
    private final MagicLoginTokenService magicLoginTokenService;
    
    public AdminUserController(UserService userService, MagicLoginTokenService magicLoginTokenService) {
        this.userService = userService;
        this.magicLoginTokenService = magicLoginTokenService;
    }
    
    /**
     * Get all users with optional filtering
     */
    @GetMapping
    public ResponseEntity<List<UserDTO>> getAllUsers(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) AuthProvider authProvider,
            @RequestParam(required = false) Boolean enabled) {
        
        log.debug("Getting all users with filters - role: {}, authProvider: {}, enabled: {}", 
                 role, authProvider, enabled);
        
        List<UserDTO> users = userService.getAllUsers(role, authProvider, enabled);
        return ResponseEntity.ok(users);
    }
    
    /**
     * Get user by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> getUserById(@PathVariable Long id) {
        log.debug("Getting user by id: {}", id);
        UserDTO user = userService.getUserById(id);
        return ResponseEntity.ok(user);
    }
    
    /**
     * Create new local user
     */
    @PostMapping
    public ResponseEntity<UserDTO> createUser(@Valid @RequestBody CreateUserDTO createUserDTO) {
        log.info("Creating new user: {}", createUserDTO.getUsername());
        
        try {
            UserDTO user = userService.createUser(createUserDTO);
            return ResponseEntity.status(HttpStatus.CREATED).body(user);
        } catch (IllegalArgumentException e) {
            log.error("Error creating user: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Update existing user
     */
    @PutMapping("/{id}")
    public ResponseEntity<UserDTO> updateUser(
            @PathVariable Long id,
            @RequestBody UpdateUserDTO updateUserDTO) {
        
        log.info("Updating user with id: {}", id);
        
        try {
            UserDTO user = userService.updateUser(id, updateUserDTO);
            return ResponseEntity.ok(user);
        } catch (IllegalArgumentException e) {
            log.error("Error updating user: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Set user password
     */
    @PutMapping("/{id}/password")
    public ResponseEntity<Void> setPassword(
            @PathVariable Long id,
            @Valid @RequestBody SetPasswordDTO setPasswordDTO) {
        
        log.info("Setting password for user with id: {}", id);
        
        try {
            userService.setPassword(id, setPasswordDTO.getPassword());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            log.error("Error setting password: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Unlock user account
     */
    @PutMapping("/{id}/unlock")
    public ResponseEntity<Void> unlockUser(@PathVariable Long id) {
        log.info("Unlocking user with id: {}", id);
        userService.unlockUser(id);
        return ResponseEntity.ok().build();
    }
    
    /**
     * Generate magic login link for a user
     * Admin-only endpoint
     */
    @PostMapping("/{id}/magic-link")
    public ResponseEntity<MagicLinkResponseDTO> generateMagicLink(
            @PathVariable Long id,
            @RequestBody(required = false) CreateMagicLinkRequestDTO request,
            @AuthenticationPrincipal Object principal) {
        
        log.info("Generating magic link for user with id: {}", id);
        
        // Get current admin username for logging
        String adminUsername = null;
        if (principal instanceof OidcUser) {
            OidcUser oidcUser = (OidcUser) principal;
            adminUsername = (String) oidcUser.getClaims().get("preferred_username");
        } else if (principal instanceof UserDetails) {
            adminUsername = ((UserDetails) principal).getUsername();
        }
        
        try {
            MagicLinkResponseDTO response = magicLoginTokenService.generateMagicLink(id, request, adminUsername);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.error("Error generating magic link: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Revoke/delete all magic login tokens for a user
     */
    @DeleteMapping("/{id}/magic-link")
    public ResponseEntity<Void> revokeMagicLinks(
            @PathVariable Long id,
            @AuthenticationPrincipal Object principal) {
        
        log.info("Revoking all magic links for user with id: {}", id);
        
        try {
            magicLoginTokenService.invalidateAllTokensForUserId(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            log.error("Error revoking magic links: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Delete user
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable Long id,
            @AuthenticationPrincipal Object principal) {
        
        log.info("Deleting user with id: {}", id);
        
        // Get current username to prevent self-deletion
        String currentUsername = null;
        if (principal instanceof OidcUser) {
            OidcUser oidcUser = (OidcUser) principal;
            currentUsername = (String) oidcUser.getClaims().get("preferred_username");
        } else if (principal instanceof UserDetails) {
            currentUsername = ((UserDetails) principal).getUsername();
        }
        
        try {
            userService.deleteUser(id, currentUsername);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            log.error("Error deleting user: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
}
