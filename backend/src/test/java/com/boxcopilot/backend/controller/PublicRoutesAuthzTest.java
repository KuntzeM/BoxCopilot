package com.boxcopilot.backend.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The public surface stays reachable anonymously (real chain) and nothing else does. */
class PublicRoutesAuthzTest extends AuthzTestSupport {

    static List<Route> publicRoutes() {
        return List.of(
                route(GET, "/api/v1/public/" + UUID.randomUUID(), ""),
                route(GET, "/api/v1/public/items/some-token/image", ""),
                route(GET, "/api/v1/public/items/some-token/image/large", ""),
                route(GET, "/api/v1/me", ""),
                route(GET, "/api/v1/csrf", ""),
                route(GET, "/actuator/health", ""),
                route(GET, "/api/v1/mcp/continue", ""));
    }

    static List<Route> nonPublicRoutes() {
        return List.of(
                route(GET, "/api/v1/boxes", ""),
                route(GET, "/api/v1/items", ""),
                route(GET, "/api/v1/items/search?q=x", ""),
                route(GET, "/api/v1/admin/users", ""),
                route(GET, "/api/v1/box-numbers/status", ""),
                route(GET, "/api/v1/does-not-exist", ""),
                // only the POST of magic-login is public
                route(GET, "/api/v1/auth/magic-login", ""),
                route(PUT, "/api/v1/auth/magic-login", ""));
    }

    @ParameterizedTest
    @MethodSource("publicRoutes")
    void publicRouteIsReachableAnonymously(Route r) throws Exception {
        int status = mockMvc.perform(build(r)).andReturn().getResponse().getStatus();
        assertThat(status).isNotIn(401, 403);
    }

    @ParameterizedTest
    @MethodSource("nonPublicRoutes")
    void everythingElseRequiresAuthentication(Route r) throws Exception {
        mockMvc.perform(build(r).with(csrf())).andExpect(status().isUnauthorized());
    }

    /** Outside /api the entry point redirects (302) to the app: anonymous callers never get content. */
    @ParameterizedTest
    @MethodSource("nonApiNonPublicPaths")
    void nonApiPathsRedirectAnonymousCallersToLogin(String path) throws Exception {
        var res = mockMvc.perform(build(route(GET, path, ""))).andReturn().getResponse();
        assertThat(res.getStatus()).isEqualTo(302);
    }

    static List<String> nonApiNonPublicPaths() {
        return List.of("/actuator/env", "/actuator/metrics", "/some/other/path");
    }

    @Test
    void meIsAnonymousNeutral() throws Exception {
        mockMvc.perform(build(route(GET, "/api/v1/me", ""))).andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void magicLoginPostIsPublicButNeedsCsrf() throws Exception {
        Route r = route(POST, "/api/v1/auth/magic-login", "{}");
        mockMvc.perform(build(r)).andExpect(status().isForbidden());
        // reaches validation (400) rather than the authentication entry point (401)
        mockMvc.perform(build(r).with(csrf())).andExpect(status().isBadRequest());
    }

    @Test
    void formLoginPostIsPublicAndRejectsBadCredentialsGenerically() throws Exception {
        mockMvc.perform(post("/api/auth/login").param("username", "nobody").param("password", "wrong").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid credentials"));
    }

    @Test
    void healthShowsNoDetails() throws Exception {
        mockMvc.perform(build(route(GET, "/actuator/health", ""))).andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.components").doesNotExist());
    }
}
