package com.boxcopilot.backend.service;

import com.boxcopilot.backend.domain.Box;
import com.boxcopilot.backend.domain.Item;
import com.boxcopilot.backend.mapper.ItemMapper;
import com.boxcopilot.backend.repository.BoxRepository;
import com.boxcopilot.backend.repository.ItemRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/** Startup image-token backfill: legacy items get tokens, healthy data costs no scan. */
class ItemServiceStartupTest {

    private final ItemRepository itemRepository = mock(ItemRepository.class);
    private final ItemService service = new ItemService(itemRepository, mock(BoxRepository.class),
            mock(ItemMapper.class), mock(ImageStorageService.class));

    @Test
    void legacyItemsGetUniqueTokens() {
        Box box = new Box("u", "A", "A", null);
        Item nullToken = new Item(box, "a");
        Item blankToken = new Item(box, "b");
        blankToken.setImageToken("  ");
        when(itemRepository.findWithoutImageToken()).thenReturn(List.of(nullToken, blankToken));

        service.ensureImageTokens();

        assertThat(nullToken.getImageToken()).isNotBlank();
        assertThat(blankToken.getImageToken()).isNotBlank().isNotEqualTo(nullToken.getImageToken());
        verify(itemRepository).saveAll(List.of(nullToken, blankToken));
        verify(itemRepository, never()).findAll();
    }

    @Test
    void healthyDatabaseSavesNothingAndNeverScansTable() {
        when(itemRepository.findWithoutImageToken()).thenReturn(List.of());

        service.initImageTokens();

        verify(itemRepository, never()).findAll();
        verify(itemRepository, never()).saveAll(anyList());
    }
}
