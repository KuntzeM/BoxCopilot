package com.boxcopilot.backend.controller;

import com.boxcopilot.backend.domain.AuthProvider;
import com.boxcopilot.backend.domain.Role;
import com.boxcopilot.backend.domain.User;
import com.boxcopilot.backend.repository.MagicLoginTokenRepository;
import com.boxcopilot.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminMagicLinkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MagicLoginTokenRepository tokenRepository;

    @Value("${frontend.url}")
    private String frontendUrl;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("link-user", "Link User", AuthProvider.LOCAL, Role.USER));
    }

    @AfterEach
    void tearDown() {
        tokenRepository.deleteAll(tokenRepository.findByUser(user));
        userRepository.delete(user);
    }

    private MockHttpServletRequestBuilder createLink(String body) {
        return post("/api/v1/admin/users/{id}/magic-link", user.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    @Test
    void expiryAboveSeventyTwoHoursIsRejected() throws Exception {
        mockMvc.perform(createLink("{\"expiresInHours\":73}")).andExpect(status().isBadRequest());
    }

    @Test
    void expiryBelowOneHourIsRejected() throws Exception {
        mockMvc.perform(createLink("{\"expiresInHours\":0}")).andExpect(status().isBadRequest());
    }

    @Test
    void maximumExpiryIsAcceptedAndLinkPointsAtTheFrontendFragmentRoute() throws Exception {
        mockMvc.perform(createLink("{\"expiresInHours\":72}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value(startsWith(frontendUrl + "/magic#t=")));
    }

    @Test
    void defaultExpiryIsUsedWithoutBody() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/{id}/magic-link", user.getId()))
                .andExpect(status().isCreated());
    }
}
