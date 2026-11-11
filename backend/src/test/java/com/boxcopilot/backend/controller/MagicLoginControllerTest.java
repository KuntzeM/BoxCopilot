package com.boxcopilot.backend.controller;

import com.boxcopilot.backend.domain.AuthProvider;
import com.boxcopilot.backend.domain.MagicLoginToken;
import com.boxcopilot.backend.domain.Role;
import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.repository.MagicLoginTokenRepository;
import com.boxcopilot.backend.repository.UserRepository;
import com.boxcopilot.backend.security.LoginThrottle;
import com.boxcopilot.backend.security.LoginThrottleProperties;
import com.boxcopilot.backend.service.CustomUserDetailsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the magic-login endpoint against the real service and repositories.
 * CSRF enforcement is asserted against the real filter chain in OAuth2SecurityConfigTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MagicLoginControllerTest {

    private static final String ENDPOINT = "/api/v1/auth/magic-login";
    private static final String TOKEN = "11111111-2222-3333-4444-555555555555";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MagicLoginTokenRepository tokenRepository;

    @Autowired
    private LoginThrottle loginThrottle;

    @Autowired
    private LoginThrottleProperties throttleProperties;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("magic-user", "Magic User", AuthProvider.LOCAL, Role.USER));
        tokenRepository.save(new MagicLoginToken(TOKEN, user, 24));
    }

    @AfterEach
    void tearDown() {
        tokenRepository.deleteAll(tokenRepository.findByUser(user));
        userRepository.delete(user);
    }

    private MockHttpServletRequestBuilder redeem(String token) {
        return post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}");
    }

    private boolean tokenIsUsed() {
        return tokenRepository.findByToken(TOKEN).orElseThrow().getUsed();
    }

    @Test
    void validTokenReturnsSuccessAndStoresAuthenticationInSession() throws Exception {
        MvcResult result = mockMvc.perform(redeem(TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        SecurityContext context = (SecurityContext) session
                .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(context).isNotNull();
        assertThat(context.getAuthentication().getPrincipal())
                .isInstanceOfSatisfying(CustomUserDetailsService.CustomUserPrincipal.class,
                        principal -> assertThat(principal.getUser().getId()).isEqualTo(user.getId()));
        assertThat(tokenIsUsed()).isTrue();
    }

    @Test
    void successfulLoginUpdatesLastLogin() throws Exception {
        mockMvc.perform(redeem(TOKEN)).andExpect(status().isOk());

        assertThat(userRepository.findById(user.getId()).orElseThrow().getLastLogin()).isNotNull();
    }

    @Test
    void successfulLoginRotatesAnExistingSessionId() throws Exception {
        MockHttpSession preLoginSession = new MockHttpSession();
        String preLoginId = preLoginSession.getId();

        MvcResult result = mockMvc.perform(redeem(TOKEN).session(preLoginSession))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getRequest().getSession(false).getId()).isNotEqualTo(preLoginId);
    }

    @Test
    void secondRedemptionOfTheSameTokenIsUnauthorized() throws Exception {
        mockMvc.perform(redeem(TOKEN)).andExpect(status().isOk());

        mockMvc.perform(redeem(TOKEN)).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownTokenIsUnauthorizedWithGenericBody() throws Exception {
        String unknownBody = mockMvc.perform(redeem("no-such-token"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(redeem(TOKEN)).andExpect(status().isOk());
        String usedBody = mockMvc.perform(redeem(TOKEN))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknownBody).isEqualTo(usedBody);
    }

    @Test
    void expiredTokenIsUnauthorizedWithSameBodyAsUnknown() throws Exception {
        MagicLoginToken token = tokenRepository.findByToken(TOKEN).orElseThrow();
        token.setExpiryDate(LocalDateTime.now().minusMinutes(1));
        tokenRepository.save(token);

        String expiredBody = mockMvc.perform(redeem(TOKEN))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknownBody = mockMvc.perform(redeem("no-such-token"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(expiredBody).isEqualTo(unknownBody);
    }

    @Test
    void disabledUserIsUnauthorizedAndTokenStaysUnused() throws Exception {
        user.setEnabled(false);
        userRepository.save(user);

        mockMvc.perform(redeem(TOKEN)).andExpect(status().isUnauthorized());

        assertThat(tokenIsUsed()).isFalse();
    }

    @Test
    void lockedUserIsUnauthorizedAndTokenStaysUnused() throws Exception {
        user.setLockedUntil(LocalDateTime.now().plusHours(1));
        userRepository.save(user);

        mockMvc.perform(redeem(TOKEN)).andExpect(status().isUnauthorized());

        assertThat(tokenIsUsed()).isFalse();
    }

    @Test
    void failedRedemptionDoesNotCreateAnAuthenticatedSession() throws Exception {
        MvcResult result = mockMvc.perform(redeem("no-such-token"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session == null
                || session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY) == null)
                .isTrue();
    }

    @Test
    void blankTokenIsRejected() throws Exception {
        mockMvc.perform(redeem("")).andExpect(status().isBadRequest());
    }

    @Test
    void missingBodyIsRejected() throws Exception {
        mockMvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void oldGetEndpointIsNoLongerMapped() throws Exception {
        MvcResult result = mockMvc.perform(get(ENDPOINT).param("token", TOKEN)).andReturn();

        assertThat(result.getResponse().getStatus()).isIn(404, 405);
        assertThat(tokenIsUsed()).isFalse();
    }

    @Test
    void failedRedemptionsFeedTheLoginThrottleOfTheCallerAddressOnly() throws Exception {
        String ip = "198.51.100.23";
        for (int i = 0; i < throttleProperties.ipLimit() - 1; i++) {
            mockMvc.perform(redeem("no-such-token").with(request -> {
                request.setRemoteAddr(ip);
                return request;
            })).andExpect(status().isUnauthorized());
        }
        assertThat(loginThrottle.isBlocked(ip, null)).isFalse();

        mockMvc.perform(redeem("no-such-token").with(request -> {
            request.setRemoteAddr(ip);
            return request;
        })).andExpect(status().isUnauthorized());

        assertThat(loginThrottle.isBlocked(ip, null)).isTrue();
        assertThat(loginThrottle.isBlocked("198.51.100.24", null)).isFalse();
    }

    @Test
    void aSuccessfulRedemptionIsNotCountedAsAFailure() throws Exception {
        String ip = "198.51.100.25";
        mockMvc.perform(redeem(TOKEN).with(request -> {
            request.setRemoteAddr(ip);
            return request;
        })).andExpect(status().isOk());

        for (int i = 0; i < throttleProperties.ipLimit() - 1; i++) {
            loginThrottle.recordFailure(ip, null);
        }
        assertThat(loginThrottle.isBlocked(ip, null)).isFalse();
    }
}
