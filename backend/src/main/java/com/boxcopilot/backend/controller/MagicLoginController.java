package com.boxcopilot.backend.controller;

import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.dto.MagicLoginRequestDTO;
import com.boxcopilot.backend.service.CustomUserDetailsService.CustomUserPrincipal;
import com.boxcopilot.backend.service.MagicLoginTokenService;
import com.boxcopilot.backend.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/auth")
public class MagicLoginController {

    private final MagicLoginTokenService magicLoginTokenService;
    private final UserService userService;
    private final SecurityContextRepository securityContextRepository;

    public MagicLoginController(MagicLoginTokenService magicLoginTokenService,
                                UserService userService,
                                SecurityContextRepository securityContextRepository) {
        this.magicLoginTokenService = magicLoginTokenService;
        this.userService = userService;
        this.securityContextRepository = securityContextRepository;
    }

    /**
     * Redeems a magic login token and starts an authenticated session.
     * Every failure yields the same 401 so the response never reveals why a token was refused.
     */
    @PostMapping("/magic-login")
    public ResponseEntity<Map<String, Object>> magicLogin(@Valid @RequestBody MagicLoginRequestDTO body,
                                                          HttpServletRequest request,
                                                          HttpServletResponse response) {
        Optional<User> user = magicLoginTokenService.consumeToken(body.token());
        if (user.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        startAuthenticatedSession(user.get(), request, response);
        userService.updateLastLogin(user.get());
        return ResponseEntity.ok(Map.of("success", true));
    }

    private void startAuthenticatedSession(User user, HttpServletRequest request, HttpServletResponse response) {
        CustomUserPrincipal principal = new CustomUserPrincipal(user);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);

        request.getSession(true);
        request.changeSessionId();
        securityContextRepository.saveContext(context, request, response);
    }
}
