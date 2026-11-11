package com.boxcopilot.backend.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.boxcopilot.backend.domain.AuthProvider;
import com.boxcopilot.backend.domain.Role;
import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.repository.UserRepository;
import com.boxcopilot.backend.service.CustomOidcUserService;
import com.boxcopilot.backend.service.CustomUserDetailsService;
import com.boxcopilot.backend.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

/**
 * Form login through the real chain and the real {@link CustomUserDetailsService}: a disabled or unknown user is
 * rejected with one generic 401 that does not reveal why, and no ERROR (with stack trace) reaches the log.
 */
@SpringJUnitWebConfig(classes = {OAuth2SecurityConfig.class, FormLoginRejectionTest.SupportConfig.class})
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "frontend.url=http://frontend.test",
        "nextcloud.logout-url=http://nextcloud.test/logout"
})
class FormLoginRejectionTest {

    private static final String GENERIC = "{\"error\":\"Invalid credentials\"}";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;
    private ListAppender<ILoggingEvent> appender;
    private Logger rootLogger;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        appender = new ListAppender<>();
        appender.start();
        rootLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        rootLogger.detachAppender(appender);
    }

    @Test
    void disabledUserGetsGenericRejectionWithoutErrorLog() throws Exception {
        User disabled = new User("off", "Off", AuthProvider.LOCAL, Role.USER);
        disabled.setPasswordHash(passwordEncoder.encode("right-password"));
        disabled.setEnabled(false);
        when(userRepository.findByUsername("off")).thenReturn(Optional.of(disabled));

        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .param("username", "off").param("password", "right-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(GENERIC));

        assertThat(appender.list).filteredOn(event -> event.getLevel().isGreaterOrEqual(Level.ERROR))
                .as("a rejected login is not a server error").isEmpty();
    }

    @Test
    void unknownUserGetsTheSameGenericRejection() throws Exception {
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .param("username", "nobody").param("password", "whatever"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(GENERIC));
    }

    @Configuration
    @EnableWebMvc
    static class SupportConfig {

        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }

        @Bean
        CustomUserDetailsService customUserDetailsService(UserRepository userRepository) {
            return new CustomUserDetailsService(userRepository);
        }

        @Bean
        UserService userService() {
            return mock(UserService.class);
        }

        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("nextcloud")
                    .clientId("client").clientSecret("secret")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("http://backend.test/login/oauth2/code/nextcloud")
                    .scope("openid")
                    .authorizationUri("http://nextcloud.test/authorize")
                    .tokenUri("http://nextcloud.test/token")
                    .build());
        }

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
