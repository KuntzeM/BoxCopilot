package com.boxcopilot.backend.config;

import com.boxcopilot.backend.domain.AuthProvider;
import com.boxcopilot.backend.domain.Role;
import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.repository.MagicLoginTokenRepository;
import com.boxcopilot.backend.repository.UserRepository;
import com.boxcopilot.backend.service.CustomOidcUserService;
import com.boxcopilot.backend.service.CustomUserDetailsService;
import com.boxcopilot.backend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives the real OIDC login filter, {@link CustomOidcUserService} and {@link UserService} together
 * to prove that disabled users end at the login failure URL, while an active user succeeds.
 * Only the identity provider (token endpoint and ID token decoding) is replaced by stubs.
 */
@SpringJUnitWebConfig(classes = {
        OAuth2SecurityConfig.class,
        OidcLoginRejectionTest.SupportConfig.class
})
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "frontend.url=http://frontend.test",
        "nextcloud.logout-url=http://nextcloud.test/logout",
        "app.admin.password=irrelevant-admin-password"
})
class OidcLoginRejectionTest {

    private static final String STATE = "test-state";
    private static final String SUBJECT = "oidc-subject-1";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reset(userRepository);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void disabledUserEndsAtFailureUrl() throws Exception {
        User disabled = nextcloudUser();
        disabled.setEnabled(false);
        when(userRepository.findByOidcSubject(SUBJECT)).thenReturn(Optional.of(disabled));

        mockMvc.perform(get("/login/oauth2/code/nextcloud")
                        .param("code", "code").param("state", STATE).session(sessionWithAuthorizationRequest()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "http://frontend.test?error=login_failed"));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void activeUserLogsInAndIsRedirectedToFrontend() throws Exception {
        User active = nextcloudUser();
        when(userRepository.findByOidcSubject(SUBJECT)).thenReturn(Optional.of(active));
        when(userRepository.save(active)).thenReturn(active);

        mockMvc.perform(get("/login/oauth2/code/nextcloud")
                        .param("code", "code").param("state", STATE).session(sessionWithAuthorizationRequest()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "http://frontend.test"));

        verify(userRepository).save(active);
    }

    private static User nextcloudUser() {
        User user = new User("ncuser", "NC User", AuthProvider.NEXTCLOUD, Role.USER);
        user.setOidcSubject(SUBJECT);
        user.setEnabled(true);
        return user;
    }

    /**
     * Stores an authorization request as the redirect to the identity provider would have done.
     */
    private static MockHttpSession sessionWithAuthorizationRequest() {
        OAuth2AuthorizationRequest authorizationRequest = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("http://nextcloud.test/authorize")
                .clientId("client")
                .redirectUri("http://localhost/login/oauth2/code/nextcloud")
                .scope("openid")
                .state(STATE)
                .attributes(attributes -> attributes.put(OAuth2ParameterNames.REGISTRATION_ID, "nextcloud"))
                .build();
        MockHttpSession session = new MockHttpSession();
        new HttpSessionOAuth2AuthorizationRequestRepository().saveAuthorizationRequest(
                authorizationRequest, requestWithSession(session), new MockHttpServletResponse());
        return session;
    }

    private static MockHttpServletRequest requestWithSession(MockHttpSession session) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        return request;
    }

    @Configuration
    @EnableWebMvc
    static class SupportConfig {

        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(4);
        }

        @Bean
        UserService userService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
            return new UserService(userRepository, passwordEncoder, mock(MagicLoginTokenRepository.class), event -> { });
        }

        @Bean
        CustomOidcUserService customOidcUserService(UserService userService) {
            return new CustomOidcUserService(userService);
        }

        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            ClientRegistration registration = ClientRegistration.withRegistrationId("nextcloud")
                    .clientId("client")
                    .clientSecret("secret")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .scope("openid")
                    .authorizationUri("http://nextcloud.test/authorize")
                    .tokenUri("http://nextcloud.test/token")
                    .build();
            return new InMemoryClientRegistrationRepository(registration);
        }

        @Bean
        OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> accessTokenResponseClient() {
            return request -> OAuth2AccessTokenResponse.withToken("access-token")
                    .tokenType(OAuth2AccessToken.TokenType.BEARER)
                    .expiresIn(300)
                    .additionalParameters(Map.of("id_token", "stub-id-token"))
                    .build();
        }

        @Bean
        JwtDecoderFactory<ClientRegistration> idTokenDecoderFactory() {
            JwtDecoder decoder = token -> Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .issuer("http://nextcloud.test")
                    .subject(SUBJECT)
                    .audience(java.util.List.of("client"))
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(300))
                    .claim("preferred_username", "ncuser")
                    .claim("name", "NC User")
                    .claim("email", "nc@example.com")
                    .build();
            return registration -> decoder;
        }

        /**
         * Supplies the OIDC user service that production wires through ServiceConfiguration.
         */
        @Bean
        static BeanPostProcessor oidcUserServiceInjector(org.springframework.beans.factory.ObjectProvider<CustomOidcUserService> oidcUserService) {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
                    if (bean instanceof OAuth2SecurityConfig config) {
                        config.setCustomOidcUserService(oidcUserService.getObject());
                    }
                    return bean;
                }
            };
        }
    }
}
