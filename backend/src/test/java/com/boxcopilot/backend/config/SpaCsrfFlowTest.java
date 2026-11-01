package com.boxcopilot.backend.config;

import com.boxcopilot.backend.service.CustomUserDetailsService;
import com.boxcopilot.backend.service.UserService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The SPA's CSRF flow against the real application chain: fetch the token from {@code /api/v1/csrf}, send it
 * back in {@code X-XSRF-TOKEN} together with the {@code XSRF-TOKEN} cookie. The header must carry the masked
 * (BREACH-protected, per request) value; the raw cookie value echoed into the header is refused, as on the 3.5
 * baseline.
 *
 * <p>A class of its own on purpose: {@code SecurityMockMvcRequestPostProcessors.csrf()} swaps the chain's token
 * repository for a session-backed test repository, so in a context shared with tests using it no cookie would
 * ever be written.
 */
@SpringJUnitWebConfig(classes = {
        OAuth2SecurityConfig.class,
        OAuth2SecurityConfigTest.SupportConfig.class,
        SpaCsrfFlowTest.CsrfEndpoint.class
})
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "frontend.url=http://frontend.test",
        "nextcloud.logout-url=http://nextcloud.test/logout"
})
class SpaCsrfFlowTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private UserService userService;

    /** Hands out the request's CSRF token the way {@code AuthController#getCsrfToken} does. */
    @RestController
    static class CsrfEndpoint {

        @GetMapping("/api/v1/csrf")
        String csrf(CsrfToken token) {
            return token.getToken();
        }
    }

    @Test
    void theMaskedTokenPassesAndTheCookieValueDoesNot() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        MvcResult csrf = mockMvc.perform(get("/api/v1/csrf")).andExpect(status().isOk()).andReturn();
        String maskedToken = csrf.getResponse().getContentAsString();
        Cookie cookie = csrf.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isFalse();
        assertThat(maskedToken).isNotBlank().isNotEqualTo(cookie.getValue());

        // Wrong credentials: 401 proves the request got past CsrfFilter to the authentication.
        mockMvc.perform(post("/api/auth/login").cookie(cookie).header("X-XSRF-TOKEN", maskedToken)
                        .param("username", "admin").param("password", "wrong"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login").cookie(cookie).header("X-XSRF-TOKEN", cookie.getValue())
                        .param("username", "admin").param("password", "wrong"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/auth/login").header("X-XSRF-TOKEN", maskedToken)
                        .param("username", "admin").param("password", "wrong"))
                .andExpect(status().isForbidden());
    }
}
