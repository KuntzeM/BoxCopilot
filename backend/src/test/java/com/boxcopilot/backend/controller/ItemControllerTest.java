package com.boxcopilot.backend.controller;

import com.boxcopilot.backend.domain.Box;
import com.boxcopilot.backend.domain.Item;
import com.boxcopilot.backend.repository.BoxRepository;
import com.boxcopilot.backend.repository.ItemRepository;
import org.junit.jupiter.api.AfterEach;
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

/** Every route under /api/v1/items through the real security chain: anonymous 401, no CSRF 403, USER smoke. */
class ItemControllerTest extends AuthzTestSupport {

    private static final String BASE = "/api/v1/items";
    private static final String ID = BASE + "/" + MISSING_ID;

    @Autowired
    private BoxRepository boxRepository;
    @Autowired
    private ItemRepository itemRepository;

    private Box from;
    private Box to;

    static List<Route> routes() {
        return List.of(
                route(GET, BASE + "/box/" + UUID.randomUUID(), ""),
                route(GET, BASE + "/search?q=x", ""),
                route(POST, BASE, "{}"),
                route(PUT, ID, "{}"),
                route(DELETE, ID, ""),
                route(POST, ID + "/image", ""),
                route(DELETE, ID + "/image", ""),
                route(PUT, ID + "/move", "{}"),
                route(PUT, BASE + "/move-bulk", "{}"));
    }

    static List<Route> stateChangingRoutes() {
        return routes().stream().filter(Route::changesState).toList();
    }

    @AfterEach
    void cleanUp() {
        if (from != null) {
            itemRepository.deleteAll(itemRepository.findAll().stream()
                    .filter(i -> i.getBox() != null && (i.getBox().getId().equals(from.getId())
                            || i.getBox().getId().equals(to.getId()))).toList());
            boxRepository.delete(from);
            boxRepository.delete(to);
            from = null;
        }
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
    void removedListAllRouteIsGone() throws Exception {
        mockMvc.perform(build(route(GET, BASE, ""))).andExpect(status().isUnauthorized());
        mockMvc.perform(build(route(GET, BASE, "")).with(asUser())).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void userCanCreateSearchMoveAndDeleteItems() throws Exception {
        from = boxRepository.save(new Box(UUID.randomUUID().toString(), "From", "x", "d"));
        to = boxRepository.save(new Box(UUID.randomUUID().toString(), "To", "x", "d"));

        mockMvc.perform(build(route(POST, BASE, "{\"name\":\"authz-lamp\",\"boxId\":" + from.getId() + "}"))
                .with(asUser()).with(csrf())).andExpect(status().isCreated());
        Item item = itemRepository.findAll().stream().filter(i -> "authz-lamp".equals(i.getName())).findFirst()
                .orElseThrow();

        mockMvc.perform(build(route(GET, BASE + "/box/" + from.getUuid(), "")).with(asUser()))
                .andExpect(status().isOk());
        mockMvc.perform(build(route(GET, BASE + "/search?q=authz-lamp", "")).with(asUser()))
                .andExpect(status().isOk());
        mockMvc.perform(build(route(PUT, BASE + "/" + item.getId(), "{\"name\":\"authz-lamp2\"}")).with(asUser())
                .with(csrf())).andExpect(status().isOk());
        mockMvc.perform(build(route(PUT, BASE + "/" + item.getId() + "/move", "{\"targetBoxId\":" + to.getId() + "}"))
                .with(asUser()).with(csrf())).andExpect(status().isOk());
        assertThat(itemRepository.findById(item.getId()).orElseThrow().getBox().getId()).isEqualTo(to.getId());
        mockMvc.perform(build(route(PUT, BASE + "/move-bulk", "{\"itemIds\":[" + item.getId() + "],\"targetBoxId\":"
                + from.getId() + "}")).with(asUser()).with(csrf())).andExpect(status().isOk());
        mockMvc.perform(build(route(DELETE, BASE + "/" + item.getId() + "/image", "")).with(asUser()).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(build(route(DELETE, BASE + "/" + item.getId(), "")).with(asUser()).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(itemRepository.existsById(item.getId())).isFalse();
    }
}
