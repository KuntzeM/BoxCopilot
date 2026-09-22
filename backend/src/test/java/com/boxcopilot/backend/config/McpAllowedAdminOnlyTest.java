package com.boxcopilot.backend.config;

import com.boxcopilot.backend.controller.AdminUserController;
import com.boxcopilot.backend.controller.AuthController;
import com.boxcopilot.backend.controller.BoxController;
import com.boxcopilot.backend.controller.BoxNumberController;
import com.boxcopilot.backend.controller.ItemController;
import com.boxcopilot.backend.controller.MagicLoginController;
import com.boxcopilot.backend.controller.PublicItemImageController;
import com.boxcopilot.backend.controller.PublicPreviewController;
import com.boxcopilot.backend.dto.CreateUserDTO;
import com.boxcopilot.backend.dto.UpdateUserDTO;
import com.boxcopilot.backend.dto.UserDTO;
import com.boxcopilot.backend.dto.UserPrincipalDTO;
import com.boxcopilot.backend.service.CustomOidcUserService;
import com.boxcopilot.backend.service.CustomUserDetailsService;
import com.boxcopilot.backend.service.MagicLoginTokenService;
import com.boxcopilot.backend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.test.context.support.WithMockUser;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves that {@code mcp_allowed} can only be changed through the admin endpoints of the real
 * {@link OAuth2SecurityConfig} chain and never through a self-service surface.
 */
@SpringJUnitWebConfig(classes = {
        OAuth2SecurityConfig.class,
        AdminUserController.class,
        McpAllowedAdminOnlyTest.SupportConfig.class
})
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "frontend.url=http://frontend.test",
        "nextcloud.logout-url=http://nextcloud.test/logout"
})
class McpAllowedAdminOnlyTest {

    private static final String GRANT_MCP = "{\"mcpAllowed\":true}";

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private MagicLoginTokenService magicLoginTokenService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @WithMockUser(roles = "USER")
    void regularUserCannotChangeMcpAllowedThroughTheAdminEndpoint() throws Exception {
        mockMvc.perform(put("/api/v1/admin/users/{id}", 7L)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GRANT_MCP))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userService);
    }

    @Test
    void anonymousCallerCannotChangeMcpAllowedThroughTheAdminEndpoint() throws Exception {
        mockMvc.perform(put("/api/v1/admin/users/{id}", 7L)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GRANT_MCP))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(userService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanChangeMcpAllowedThroughTheAdminEndpoint() throws Exception {
        when(userService.updateUser(eq(7L), any(UpdateUserDTO.class))).thenReturn(new UserDTO());

        mockMvc.perform(put("/api/v1/admin/users/{id}", 7L)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GRANT_MCP))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateUserDTO> update = ArgumentCaptor.forClass(UpdateUserDTO.class);
        verify(userService).updateUser(eq(7L), update.capture());
        assertThat(update.getValue().getMcpAllowed()).isTrue();
    }

    @Test
    void onlyAdminUserEndpointsAcceptTheUpdateUserDto() {
        List<String> acceptingControllers = Arrays.stream(controllerClasses())
                .filter(controller -> acceptsParameter(controller, UpdateUserDTO.class))
                .map(Class::getName)
                .toList();

        assertThat(acceptingControllers).containsExactly(AdminUserController.class.getName());
    }

    /**
     * {@code /api/v1/me} reports {@code mcpAllowed} so that the SPA knows whether to offer the connection
     * dialog. That is a read-only answer: no controller takes a {@link UserPrincipalDTO} as input, so the field
     * can never travel the other way and grant the flag to its owner.
     */
    @Test
    void thePrincipalDtoReportsMcpAllowedButIsNeverAcceptedAsInput() {
        assertThat(Arrays.stream(UserPrincipalDTO.class.getDeclaredFields()).map(field -> field.getName()))
                .contains("mcpAllowed");

        List<String> acceptingControllers = Arrays.stream(controllerClasses())
                .filter(controller -> acceptsParameter(controller, UserPrincipalDTO.class))
                .map(Class::getName)
                .toList();

        assertThat(acceptingControllers).isEmpty();
    }

    @Test
    void createUserDtoDoesNotCarryMcpAllowed() {
        assertThat(Arrays.stream(CreateUserDTO.class.getDeclaredFields()).map(field -> field.getName()))
                .doesNotContain("mcpAllowed");
    }

    private static Class<?>[] controllerClasses() {
        return new Class<?>[] {
                AdminUserController.class,
                AuthController.class,
                MagicLoginController.class,
                BoxNumberController.class,
                PublicItemImageController.class,
                PublicPreviewController.class,
                BoxController.class,
                ItemController.class
        };
    }

    private static boolean acceptsParameter(Class<?> controller, Class<?> parameterType) {
        return Arrays.stream(controller.getDeclaredMethods())
                .map(Method::getParameterTypes)
                .flatMap(Arrays::stream)
                .anyMatch(parameterType::equals);
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
