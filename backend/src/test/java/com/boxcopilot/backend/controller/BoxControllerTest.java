package com.boxcopilot.backend.controller;

import com.boxcopilot.backend.domain.Box;
import com.boxcopilot.backend.repository.BoxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Every route under /api/v1/boxes through the real security chain: anonymous 401, no CSRF 403, USER smoke. */
class BoxControllerTest extends AuthzTestSupport {

    private static final String BASE = "/api/v1/boxes";
    private static final String ID = BASE + "/" + MISSING_ID;

    @Autowired
    private BoxRepository boxRepository;

    static List<Route> routes() {
        return List.of(
                route(GET, BASE, ""),
                route(GET, BASE + "/" + UUID.randomUUID(), ""),
                route(POST, BASE, "{}"),
                route(PUT, ID, "{}"),
                route(POST, ID + "/rotate-share", ""),
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
    @MethodSource("stateChangingRoutes")
    void stateChangingWithoutCsrfTokenGets403(Route r) throws Exception {
        mockMvc.perform(build(r).with(asUser())).andExpect(status().isForbidden());
        mockMvc.perform(build(r)).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @MethodSource("routes")
    void plainUserIsNotRejectedByAuthorization(Route r) throws Exception {
        int status = mockMvc.perform(build(r).with(asUser()).with(csrf())).andReturn().getResponse().getStatus();
        assertThat(status).isNotIn(401, 403);
    }

    @Test
    void userCanCreateReadRotateAndDeleteABox() throws Exception {
        mockMvc.perform(build(route(POST, BASE, "{\"currentRoom\":\"AuthzCellar\",\"targetRoom\":\"Attic\"}"))
                .with(asUser()).with(csrf())).andExpect(status().isCreated());
        Box box = boxRepository.findAll().stream().filter(b -> "AuthzCellar".equals(b.getCurrentRoom()))
                .findFirst().orElseThrow();
        try {
            mockMvc.perform(build(route(GET, BASE, "")).with(asUser())).andExpect(status().isOk());
            mockMvc.perform(build(route(GET, BASE + "/" + box.getUuid(), "")).with(asUser()))
                    .andExpect(status().isOk());
            mockMvc.perform(build(route(PUT, BASE + "/" + box.getId(), "{\"description\":\"d\"}")).with(asUser())
                    .with(csrf())).andExpect(status().isOk());
            mockMvc.perform(build(route(POST, BASE + "/" + box.getId() + "/rotate-share", "")).with(asUser())
                    .with(csrf())).andExpect(status().isOk());
        } finally {
            mockMvc.perform(build(route(DELETE, BASE + "/" + box.getId(), "")).with(asUser()).with(csrf()))
                    .andExpect(status().isNoContent());
        }
        assertThat(boxRepository.existsById(box.getId())).isFalse();
    }
}
