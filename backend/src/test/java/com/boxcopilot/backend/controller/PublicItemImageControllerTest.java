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
    void imagesAreImmutablyCachedAndCarryAnHttpDate() throws Exception {
        ItemService items = mock(ItemService.class);
        when(items.getImageByToken("t")).thenReturn(new ByteArrayResource(new byte[]{1}));
        when(items.getImageLastModifiedByToken("t")).thenReturn(1_700_000_000_000L);
        when(items.getLargeImageByToken("t")).thenReturn(new ByteArrayResource(new byte[]{1}));
        when(items.getLargeImageLastModifiedByToken("t")).thenReturn(1_700_000_000_000L);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PublicItemImageController(items)).build();

        for (String path : new String[]{"/api/v1/public/items/t/image", "/api/v1/public/items/t/image/large"}) {
            mvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "public, max-age=31536000, immutable"))
                    .andExpect(header().string("Last-Modified", "Tue, 14 Nov 2023 22:13:20 GMT"));
        }
    }
}
