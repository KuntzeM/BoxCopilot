package com.boxcopilot.backend.controller;

import com.boxcopilot.backend.testutil.WithMockOidcUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for AuthController.
 * Demonstrates usage of @WithMockOidcUser for testing authenticated endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockOidcUser(
        sub = "test-user-123",
        email = "testuser@example.com",
        name = "Test User",
        preferredUsername = "testuser"
    )
    void testGetCurrentUser_withAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.sub").value("test-user-123"))
            .andExpect(jsonPath("$.email").value("testuser@example.com"))
            .andExpect(jsonPath("$.name").value("Test User"))
            .andExpect(jsonPath("$.preferredUsername").value("testuser"));
            // Note: CSRF token is null in test profile since CSRF is disabled
    }

    @Test
    void testGetCurrentUser_withoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(false))
            .andExpect(jsonPath("$.sub").doesNotExist())
            .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void testGetCsrfToken() throws Exception {
        // In test profile, CSRF is disabled, so this endpoint returns an error
        // This test validates that the endpoint handles the case properly
        mockMvc.perform(get("/api/v1/csrf"))
            .andExpect(status().is5xxServerError())
            .andExpect(jsonPath("$.error").value("CSRF token not available"));
    }
}
