package com.boxcopilot.backend.service;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.boxcopilot.backend.mapper.ItemMapper;
import com.boxcopilot.backend.repository.BoxRepository;
import com.boxcopilot.backend.repository.ItemRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Startup image-token backfill: legacy items get tokens, healthy data costs no scan. */
class ItemServiceStartupTest {

    private final ItemRepository itemRepository = mock(ItemRepository.class);
    private final ItemService service = new ItemService(itemRepository, mock(BoxRepository.class),
            mock(ItemMapper.class), mock(ImageStorageService.class), new TransactionTemplate(mock(PlatformTransactionManager.class)));

    @Test
    void legacyItemsGetUniqueTokensWithoutLoadingEntities() {
        when(itemRepository.findIdsWithoutImageToken()).thenReturn(List.of(1L, 2L));

        service.initImageTokens();

        ArgumentCaptor<String> tokens = ArgumentCaptor.forClass(String.class);
        verify(itemRepository).updateImageToken(eq(1L), tokens.capture());
        verify(itemRepository).updateImageToken(eq(2L), tokens.capture());
        assertThat(tokens.getAllValues()).doesNotHaveDuplicates().allSatisfy(t -> assertThat(t).isNotBlank());
        verify(itemRepository, never()).findAll();
    }

    @Test
    void healthyDatabaseUpdatesNothingAndNeverScansTable() {
        when(itemRepository.findIdsWithoutImageToken()).thenReturn(List.of());

        service.initImageTokens();

        verify(itemRepository, never()).findAll();
        verify(itemRepository, never()).updateImageToken(any(), any());
    }
}
