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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The only unauthenticated data path: the frontend branches on 404 ("link invalid") vs. anything else. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicPreviewControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private BoxRepository boxRepository;
    @Autowired private ItemRepository itemRepository;

    private Box box;
    private Box other;
    private Item withImage;
    private Item plain;
    private Item foreign;

    @BeforeEach
    void setUp() {
        box = boxRepository.save(new Box(UUID.randomUUID().toString(), "Cellar", "Kitchen", "Winter things"));
        other = boxRepository.save(new Box(UUID.randomUUID().toString(), "Attic", "Office", "Secret"));
        withImage = new Item(box, "Alpha lamp");
        withImage.setImagePath("1_thumb.jpg");
        withImage.setImageToken("tok-" + UUID.randomUUID());
        withImage.setImageUpdatedAt(1234L);
        plain = new Item(box, "Beta book");
        foreign = new Item(other, "Foreign thing");
        itemRepository.save(withImage);
        itemRepository.save(plain);
        itemRepository.save(foreign);
    }

    @AfterEach
    void tearDown() {
        itemRepository.deleteAll(java.util.List.of(withImage, plain, foreign));
        boxRepository.deleteAll(java.util.List.of(box, other));
    }

    @Test
    void anonymousPreviewListsItemsWithCacheBustedImageUrl() throws Exception {
        mockMvc.perform(get("/api/v1/public/{uuid}", box.getUuid()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid").value(box.getUuid()))
                .andExpect(jsonPath("$.currentRoom").value("Cellar"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].name").value("Alpha lamp"))
                .andExpect(jsonPath("$.items[0].imageUrl")
                        .value("/api/v1/public/items/" + withImage.getImageToken() + "/image?t=1234"))
                .andExpect(jsonPath("$.items[1].name").value("Beta book"))
                .andExpect(jsonPath("$.items[1].imageUrl").doesNotExist());
    }

    @Test
    void previewNeverContainsAnotherBoxsData() throws Exception {
        mockMvc.perform(get("/api/v1/public/{uuid}", box.getUuid()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Foreign thing"))))
                .andExpect(content().string(not(containsString(other.getUuid()))));
    }

    @Test
    void unknownUuidAnswers404WithJsonErrorBody() throws Exception {
        String unknown = UUID.randomUUID().toString();
        mockMvc.perform(get("/api/v1/public/{uuid}", unknown))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Box not found"));
    }
}
