package com.boxcopilot.backend.config;

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
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real {@link OAuth2SecurityConfig} filter chain, never the permissive test chain.
 */
@SpringJUnitWebConfig(classes = {
        OAuth2SecurityConfig.class,
        OAuth2SecurityConfigTest.SupportConfig.class
})
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "frontend.url=http://frontend.test",
        "nextcloud.logout-url=http://nextcloud.test/logout"
})
class OAuth2SecurityConfigTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private UserService userService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void loginPostWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .param("username", "admin")
                        .param("password", "wrong"))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginPostWithCsrfTokenReachesAuthentication() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .param("username", "admin")
                        .param("password", "wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void magicLoginPostWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/magic-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"abc\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void magicLoginPostWithCsrfTokenPassesTheChainWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/auth/magic-login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"abc\"}"))
                .andExpect(status().isNotFound());
    }

    /**
     * Only the POST is public. The GET variant was removed with the magic-link rework, and leaving it open would
     * keep a token-in-URL endpoint reachable, so the chain must no longer let it through.
     */
    @Test
    void magicLoginGetIsNoLongerPublic() throws Exception {
        mockMvc.perform(get("/api/v1/auth/magic-login").param("token", "abc"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void oauth2AuthorizationEndpointStaysPublic() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/nextcloud"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith("http://nextcloud.test/authorize")));
    }

    @Test
    void otherOauth2PathsRequireAuthentication() throws Exception {
        for (String path : new String[] {"/oauth2/token", "/oauth2/authorize", "/oauth2/jwks", "/oauth2/introspect"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(header().string("Location", "http://frontend.test"));
        }
    }

    @Test
    void securityFilterChainHasOrderThree() {
        Method chain = Arrays.stream(OAuth2SecurityConfig.class.getDeclaredMethods())
                .filter(method -> SecurityFilterChain.class.equals(method.getReturnType()))
                .findFirst()
                .orElseThrow();

        Order order = chain.getAnnotation(Order.class);

        assertThat(order).isNotNull();
        assertThat(order.value()).isEqualTo(3);
    }

    @Configuration
    @EnableWebMvc
    static class SupportConfig {

        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            ClientRegistration registration = ClientRegistration.withRegistrationId("nextcloud")
                    .clientId("client")
                    .clientSecret("secret")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("http://backend.test/login/oauth2/code/nextcloud")
                    .scope("openid")
                    .authorizationUri("http://nextcloud.test/authorize")
                    .tokenUri("http://nextcloud.test/token")
                    .build();
            return new InMemoryClientRegistrationRepository(registration);
        }

        /**
         * Supplies the OIDC user service that production wires through ServiceConfiguration.
         */
        @Bean
        static BeanPostProcessor oidcUserServiceInjector() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
                    if (bean instanceof OAuth2SecurityConfig config) {
                        config.setCustomOidcUserService(mock(CustomOidcUserService.class));
                    }
                    return bean;
                }
            };
        }
    }
}
