package com.boxcopilot.backend.controller;

import com.boxcopilot.backend.domain.Box;
import com.boxcopilot.backend.domain.Item;
import com.boxcopilot.backend.repository.BoxRepository;
import com.boxcopilot.backend.repository.ItemRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Revoking a share link: old public uuid / image tokens die (404), new ones work. Auth comes from the class-level @PreAuthorize and the dev/prod chain (not active in the test profile). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BoxControllerRotateShareTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private BoxRepository boxRepository;
    @Autowired private ItemRepository itemRepository;

    private Box box;
    private Item withImage;
    private Item plain;
    private String oldUuid;
    private String oldToken;

    @BeforeEach
    void setUp() {
        oldUuid = UUID.randomUUID().toString();
        oldToken = "tok-" + UUID.randomUUID();
        box = boxRepository.save(new Box(oldUuid, "Cellar", "Kitchen", "Winter things"));
        withImage = new Item(box, "Alpha lamp");
        withImage.setImagePath("1_thumb.jpg");
        withImage.setImageToken(oldToken);
        withImage.setImageUpdatedAt(1234L);
        plain = new Item(box, "Beta book");
        itemRepository.save(withImage);
        itemRepository.save(plain);
    }

    @AfterEach
    void tearDown() {
        itemRepository.deleteAll(List.of(withImage, plain));
        boxRepository.delete(box);
    }

    @Test
    @WithMockUser
    void rotationKillsOldLinksAndNewOnesWork() throws Exception {
        mockMvc.perform(get("/api/v1/public/{uuid}", oldUuid)).andExpect(status().isOk());

        MvcResult res = mockMvc.perform(post("/api/v1/boxes/{id}/rotate-share", box.getId()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(box.getId()))
                .andExpect(jsonPath("$.currentRoom").value("Cellar"))
                .andReturn();
        String newUuid = boxRepository.findById(box.getId()).orElseThrow().getUuid();
        assertThat(newUuid).isNotEqualTo(oldUuid);
        assertThat(res.getResponse().getContentAsString()).contains(newUuid).doesNotContain(oldUuid);

        mockMvc.perform(get("/api/v1/public/{uuid}", oldUuid)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/public/items/{t}/image", oldToken)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/public/items/{t}/image/large", oldToken)).andExpect(status().isNotFound());

        Item rotated = itemRepository.findById(withImage.getId()).orElseThrow();
        assertThat(rotated.getImageToken()).isNotEqualTo(oldToken).isNotBlank();
        assertThat(rotated.getImageUpdatedAt()).isEqualTo(1234L);
        assertThat(itemRepository.findById(plain.getId()).orElseThrow().getImageToken()).isNull();
        mockMvc.perform(get("/api/v1/public/{uuid}", newUuid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].imageUrl")
                        .value("/api/v1/public/items/" + rotated.getImageToken() + "/image?t=1234"));
    }

    @Test
    @WithMockUser
    void unknownBoxIs404() throws Exception {
        mockMvc.perform(post("/api/v1/boxes/{id}/rotate-share", 999_999_999L).with(csrf()))
                .andExpect(status().isNotFound());
    }
}
