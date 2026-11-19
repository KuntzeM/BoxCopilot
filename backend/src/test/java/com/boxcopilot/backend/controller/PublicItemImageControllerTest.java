package com.boxcopilot.backend.controller;

import com.boxcopilot.backend.service.ItemService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicItemImageControllerTest {

    @Test
    void tokenWithoutImageFileIs404() throws Exception {
        ItemService items = mock(ItemService.class);
        when(items.getImageByToken("t")).thenThrow(new com.boxcopilot.backend.exception.ImageNotFoundException("Image not found"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PublicItemImageController(items))
                .setControllerAdvice(new com.boxcopilot.backend.exception.GlobalExceptionHandler()).build();

        mvc.perform(get("/api/v1/public/items/t/image")).andExpect(status().isNotFound());
    }

    @Test
    void deletedImageIs404ForBothEndpointsEvenWhenOnlyImagesAreAccepted() throws Exception {
        ItemService items = mock(ItemService.class);
        when(items.getImageByToken("t")).thenThrow(new com.boxcopilot.backend.exception.ImageNotFoundException("Image not found"));
        when(items.getLargeImageByToken("t")).thenThrow(new com.boxcopilot.backend.exception.ImageNotFoundException("Large image not found"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PublicItemImageController(items))
                .setControllerAdvice(new com.boxcopilot.backend.exception.GlobalExceptionHandler()).build();

        for (String path : new String[]{"/api/v1/public/items/t/image", "/api/v1/public/items/t/image/large"}) {
            mvc.perform(get(path).accept("image/avif,image/webp,image/apng,image/*,*/*;q=0.8"))
                    .andExpect(status().isNotFound());
            mvc.perform(get(path).accept("image/jpeg"))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void unknownTokenIs404ForBothEndpointsEvenWhenOnlyImagesAreAccepted() throws Exception {
        ItemService items = mock(ItemService.class);
        when(items.getImageByToken("t")).thenThrow(new com.boxcopilot.backend.service.ResourceNotFoundException("Item not found for image token"));
        when(items.getLargeImageByToken("t")).thenThrow(new com.boxcopilot.backend.service.ResourceNotFoundException("Item not found for image token"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PublicItemImageController(items))
                .setControllerAdvice(new com.boxcopilot.backend.exception.GlobalExceptionHandler()).build();

        for (String path : new String[]{"/api/v1/public/items/t/image", "/api/v1/public/items/t/image/large"}) {
            mvc.perform(get(path).accept("image/avif,image/webp,image/apng,image/*,*/*;q=0.8"))
                    .andExpect(status().isNotFound());
            mvc.perform(get(path).accept("image/jpeg"))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void imagesAreBrieflyCachedAndCarryAnHttpDate() throws Exception {
        ItemService items = mock(ItemService.class);
        when(items.getImageByToken("t")).thenReturn(new ByteArrayResource(new byte[]{1}));
        when(items.getImageLastModifiedByToken("t")).thenReturn(1_700_000_000_000L);
        when(items.getLargeImageByToken("t")).thenReturn(new ByteArrayResource(new byte[]{1}));
        when(items.getLargeImageLastModifiedByToken("t")).thenReturn(1_700_000_000_000L);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PublicItemImageController(items)).build();

        for (String path : new String[]{"/api/v1/public/items/t/image", "/api/v1/public/items/t/image/large"}) {
            mvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "public, max-age=3600"))
                    .andExpect(header().string("Last-Modified", "Tue, 14 Nov 2023 22:13:20 GMT"));
        }
    }
}
