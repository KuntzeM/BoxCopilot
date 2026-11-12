package com.boxcopilot.backend.controller;

import com.boxcopilot.backend.domain.AuthProvider;
import com.boxcopilot.backend.domain.Role;
import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.repository.UserRepository;
import com.boxcopilot.backend.service.CustomUserDetailsService.CustomUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Shared setup for the authorization matrix tests. Unlike the "test" profile (no auth chain), the "dev" profile loads
 * the real {@code OAuth2SecurityConfig} filter chain, so 401/403 here are the production decisions. Sessions are
 * simulated with a slim {@link CustomUserPrincipal} of a persisted user, which keeps {@code SessionRevalidationFilter}
 * satisfied (user exists, enabled, role matches).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:authz;DB_CLOSE_DELAY=-1",
        "app.image.storage.path=target/authz-images"})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(AuthzTestSupport.OidcSupport.class)
abstract class AuthzTestSupport {

    static final long MISSING_ID = 999_999L;

    /** One HTTP route plus a minimal body, so authorized calls reach the handler (400/404 is fine, 401/403 is not). */
    record Route(HttpMethod method, String path, String body) {
        @Override
        public String toString() {
            return method + " " + path;
        }

        boolean changesState() {
            return method != HttpMethod.GET;
        }
    }

    static Route route(HttpMethod method, String path, String body) {
        return new Route(method, path, body);
    }

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected UserRepository userRepository;

    protected User user;
    protected User admin;

    @BeforeEach
    void persistPrincipals() {
        user = userRepository.findByUsername("authz-user")
                .orElseGet(() -> userRepository.save(new User("authz-user", "Authz User", AuthProvider.LOCAL, Role.USER)));
        admin = userRepository.findByUsername("authz-admin")
                .orElseGet(() -> userRepository.save(new User("authz-admin", "Authz Admin", AuthProvider.LOCAL, Role.ADMIN)));
    }

    protected RequestPostProcessor asUser() {
        return SecurityMockMvcRequestPostProcessors.user(new CustomUserPrincipal(user));
    }

    protected RequestPostProcessor asAdmin() {
        return SecurityMockMvcRequestPostProcessors.user(new CustomUserPrincipal(admin));
    }

    protected RequestPostProcessor csrf() {
        return SecurityMockMvcRequestPostProcessors.csrf();
    }

    protected MockHttpServletRequestBuilder build(Route r) {
        return MockMvcRequestBuilders.request(r.method(), r.path())
                .contentType(MediaType.APPLICATION_JSON).content(r.body());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class OidcSupport {
        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("nextcloud")
                    .clientId("test-client").clientSecret("test-secret")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .redirectUri("http://localhost:8080/login/oauth2/code/{registrationId}")
                    .scope("openid")
                    .authorizationUri("http://localhost/authorize").tokenUri("http://localhost/token")
                    .jwkSetUri("http://localhost/jwks").issuerUri("http://localhost")
                    .userNameAttributeName(IdTokenClaimNames.SUB).build());
        }
    }
}
