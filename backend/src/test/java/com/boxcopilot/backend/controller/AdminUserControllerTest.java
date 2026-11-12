package com.boxcopilot.backend.controller;

import com.boxcopilot.backend.domain.AuthProvider;
import com.boxcopilot.backend.domain.Role;
import com.boxcopilot.backend.domain.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Every route under /api/v1/admin/users through the real security chain: anonymous 401, USER 403, ADMIN passes. */
class AdminUserControllerTest extends AuthzTestSupport {

    private static final String BASE = "/api/v1/admin/users";
    private static final String ID = BASE + "/" + MISSING_ID;

    static List<Route> routes() {
        return List.of(
                route(GET, BASE, ""),
                route(GET, ID, ""),
                route(POST, BASE, "{}"),
                route(PUT, ID, "{}"),
                route(PUT, ID + "/password", "{\"password\":\"a-long-enough-password\"}"),
                route(POST, ID + "/magic-link", ""),
                route(DELETE, ID + "/magic-link", ""),
                route(DELETE, ID, ""));
    }

    static List<Route> stateChangingRoutes() {
        return routes().stream().filter(Route::changesState).toList();
    }

    @ParameterizedTest
    @MethodSource("routes")
    void anonymousGets401(Route r) throws Exception {
        mockMvc.perform(build(r).with(csrf())).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @MethodSource("routes")
    void userGets403(Route r) throws Exception {
        mockMvc.perform(build(r).with(asUser()).with(csrf())).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @MethodSource("routes")
    void adminIsNeitherUnauthorizedNorForbidden(Route r) throws Exception {
        int status = mockMvc.perform(build(r).with(asAdmin()).with(csrf())).andReturn().getResponse().getStatus();
        assertThat(status).isNotIn(401, 403);
    }

    @ParameterizedTest
    @MethodSource("stateChangingRoutes")
    void stateChangingWithoutCsrfTokenGets403EvenForAdmin(Route r) throws Exception {
        mockMvc.perform(build(r).with(asAdmin())).andExpect(status().isForbidden());
        mockMvc.perform(build(r)).andExpect(status().isForbidden());
    }

    @Test
    void unlockEndpointNoLongerExists() throws Exception {
        mockMvc.perform(build(route(PUT, ID + "/unlock", "")).with(asAdmin()).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCannotDeleteThemselves() throws Exception {
        mockMvc.perform(build(route(DELETE, BASE + "/" + admin.getId(), "")).with(asAdmin()).with(csrf()))
                .andExpect(status().isBadRequest());
        assertThat(userRepository.existsById(admin.getId())).isTrue();
    }

    @Test
    void adminCanDeleteAnotherUser() throws Exception {
        User other = userRepository.save(new User("del-" + UUID.randomUUID().toString().substring(0, 8), "Other",
                AuthProvider.LOCAL, Role.USER));
        mockMvc.perform(build(route(DELETE, BASE + "/" + other.getId(), "")).with(asAdmin()).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(userRepository.existsById(other.getId())).isFalse();
    }

    @Test
    void settingAPasswordForAnOidcUserIsRejectedAndChangesNothing() throws Exception {
        User oidc = userRepository.save(new User("oidc-" + UUID.randomUUID().toString().substring(0, 8), "Oidc",
                AuthProvider.NEXTCLOUD, Role.USER));
        try {
            mockMvc.perform(build(route(PUT, BASE + "/" + oidc.getId() + "/password",
                            "{\"password\":\"a-long-enough-password\"}")).with(asAdmin()).with(csrf()))
                    .andExpect(status().isBadRequest());
            User reloaded = userRepository.findById(oidc.getId()).orElseThrow();
            assertThat(reloaded.getPasswordHash()).isNull();
            assertThat(reloaded.getSessionsValidAfter()).isNull();
        } finally {
            userRepository.delete(oidc);
        }
    }
}
