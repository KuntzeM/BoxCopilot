package com.boxcopilot.backend.config;

import com.boxcopilot.backend.repository.UserRepository;
import com.boxcopilot.backend.security.LoginThrottle;
import com.boxcopilot.backend.security.LoginThrottleFilter;
import com.boxcopilot.backend.security.LoginThrottleProperties;
import org.springframework.beans.factory.ObjectProvider;
import java.time.Clock;
import java.time.Duration;
import com.boxcopilot.backend.service.CustomOidcUserService;
import com.boxcopilot.backend.service.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile({"dev", "prod"})
public class OAuth2SecurityConfig {

    @Value("${frontend.url}")
    private String frontendUrl;

    @Value("${nextcloud.logout-url}")
    private String nextcloudLogoutUrl;

    private final CustomUserDetailsService customUserDetailsService;
    private CustomOidcUserService customOidcUserService; // Set via setter to avoid circular dependency

    public OAuth2SecurityConfig(CustomUserDetailsService customUserDetailsService) {
        this.customUserDetailsService = customUserDetailsService;
    }
    
    /**
     * Set CustomOidcUserService via setter to avoid circular dependency
     * Called by ServiceConfiguration after all beans are created
     */
    public void setCustomOidcUserService(CustomOidcUserService customOidcUserService) {
        this.customOidcUserService = customOidcUserService;
    }
    
    private LoginThrottle fallbackThrottle;

    /**
     * The application always provides the {@link LoginThrottle} bean (SecurityBeansConfiguration); the default-limit
     * fallback only keeps slices that load this class alone working, and is shared by the filter and the handler.
     */
    private synchronized LoginThrottle throttle(ObjectProvider<LoginThrottle> provider) {
        LoginThrottle bean = provider.getIfAvailable();
        if (bean != null) {
            return bean;
        }
        if (fallbackThrottle == null) {
            fallbackThrottle = new LoginThrottle(Clock.systemUTC(),
                    new LoginThrottleProperties(10, 5, Duration.ofMinutes(15)));
        }
        return fallbackThrottle;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    @Order(3)
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                           AuthenticationSuccessHandler authenticationSuccessHandler,
                                           AuthenticationFailureHandler authenticationFailureHandler,
                                           SecurityContextRepository securityContextRepository,
                                           UserRepository userRepository,
                                           ObjectProvider<LoginThrottle> loginThrottle) throws Exception {

        http
            .securityContext(context -> context.securityContextRepository(securityContextRepository))
            .addFilterAfter(new SessionRevalidationFilter(userRepository), SecurityContextHolderFilter.class)
            // After the CSRF filter, before form login: answers 429 before any DB/bcrypt work
            .addFilterBefore(new LoginThrottleFilter(throttle(loginThrottle)), UsernamePasswordAuthenticationFilter.class)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            )
            .authorizeHttpRequests(auth -> auth
                // Admin endpoints - require ADMIN role
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                // Public endpoints - no authentication required
                .requestMatchers("/api/public/**", "/api/v1/public/**").permitAll()
                .requestMatchers("/api/v1/public/items/**").permitAll()
                .requestMatchers("/api/v1/me", "/api/v1/csrf").permitAll()
                .requestMatchers("/api/auth/login").permitAll()
                // Only the POST redeems a magic link; a GET would put the token into URLs, logs and referrers
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/magic-login").permitAll()
                // Resumes an interrupted MCP authorization. Public so that an expired session yields a redirect to
                // the frontend instead of a 401 in a top-level navigation; the handler requires authentication
                // itself. The literal keeps this configuration free of any dependency on the mcp package.
                .requestMatchers(HttpMethod.GET, "/api/v1/mcp/continue").permitAll()
                .requestMatchers("/login/**", "/oauth2/authorization/**", "/error").permitAll()
                // All other API endpoints require authentication
                .requestMatchers("/api/**").authenticated()
                .anyRequest().authenticated()
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(apiAuthenticationEntryPoint())
            )
            .formLogin(form -> form
                .loginProcessingUrl("/api/auth/login")
                .successHandler(authenticationSuccessHandler)
                .failureHandler(authenticationFailureHandler)
                .permitAll()
            )
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo -> userInfo
                    .oidcUserService(customOidcUserService)
                )
                .defaultSuccessUrl(frontendUrl, true)
                .failureUrl(frontendUrl + "?error=login_failed")
            )
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler(oidcLogoutSuccessHandler())
                .invalidateHttpSession(true)
                .deleteCookies("SESSION", "XSRF-TOKEN")
            )
            .userDetailsService(customUserDetailsService);

        return http.build();
    }

    /**
     * Custom authentication entry point for API requests
     * Returns 401 for API requests instead of redirecting
     */
    @Bean
    AuthenticationEntryPoint apiAuthenticationEntryPoint() {
        return new AuthenticationEntryPoint() {
            @Override
            public void commence(HttpServletRequest request, HttpServletResponse response,
                                AuthenticationException authException) throws IOException {
                String requestUri = request.getRequestURI();
                
                // For API requests, return 401 Unauthorized
                if (requestUri.startsWith("/api/")) {
                    response.setContentType("application/json");
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write("{\"error\":\"Unauthorized\"}");
                } else {
                    // For non-API requests, redirect to login
                    response.sendRedirect(frontendUrl);
                }
            }
        };
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(frontendUrl));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*", "X-XSRF-TOKEN"));
        configuration.setAllowCredentials(true);
        // Expose CSRF header name used by Axios (optional; cookie is primary)
        configuration.setExposedHeaders(Arrays.asList("X-XSRF-TOKEN"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    LogoutSuccessHandler oidcLogoutSuccessHandler() {
        return new LogoutSuccessHandler() {
            @Override
            public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
                // Redirect to Nextcloud logout to end the OIDC session
                response.sendRedirect(nextcloudLogoutUrl + "?redirect_uri=" + frontendUrl);
            }
        };
    }
    
    /**
     * Authentication success handler bean
     * Injected into SecurityFilterChain to avoid circular dependency
     */
    @Bean
    AuthenticationSuccessHandler authenticationSuccessHandler(com.boxcopilot.backend.service.UserService userService) {
        return (request, response, authentication) -> {
            // Update last login timestamp
            if (authentication.getPrincipal() instanceof CustomUserDetailsService.CustomUserPrincipal) {
                CustomUserDetailsService.CustomUserPrincipal principal = 
                    (CustomUserDetailsService.CustomUserPrincipal) authentication.getPrincipal();
                userService.updateLastLogin(principal.getUser());
            }
            
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":true}");
        };
    }
    
    /**
     * Authentication failure handler bean
     * Injected into SecurityFilterChain to avoid circular dependency
     */
    @Bean
    AuthenticationFailureHandler authenticationFailureHandler(ObjectProvider<LoginThrottle> loginThrottle) {
        LoginThrottle throttle = throttle(loginThrottle);
        return (request, response, exception) -> {
            LoginThrottleFilter.recordFailure(throttle, request, request.getParameter("username"));
            // One generic answer for wrong password, unknown user and disabled account (no account enumeration)
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Invalid credentials\"}");
        };
    }
}
