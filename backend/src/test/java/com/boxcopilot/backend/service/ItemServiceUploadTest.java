package com.boxcopilot.backend.service;

import com.boxcopilot.backend.domain.Item;
import com.boxcopilot.backend.exception.InvalidImageException;
import com.boxcopilot.backend.mapper.ItemMapper;
import com.boxcopilot.backend.repository.BoxRepository;
import com.boxcopilot.backend.repository.ItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ItemServiceUploadTest {

    @Test
    void rejectedUploadLeavesTheExistingImageAndItemUntouched() {
        ItemRepository items = mock(ItemRepository.class);
        ImageStorageService storage = mock(ImageStorageService.class);
        ItemService service = new ItemService(items, mock(BoxRepository.class), mock(ItemMapper.class), storage);
        Item item = new Item();
        item.setImagePath("1_thumb.jpg");
        when(items.findById(1L)).thenReturn(Optional.of(item));
        MockMultipartFile file = new MockMultipartFile("file", "x.png", "image/png", new byte[]{1});
        when(storage.saveImage(1L, file)).thenThrow(new InvalidImageException("File is not a readable image"));

        assertThatThrownBy(() -> service.uploadImage(1L, file)).isInstanceOf(InvalidImageException.class);

        verify(storage, never()).deleteImage(any());
        verify(items, never()).save(any());
        assertThat(item.getImagePath()).isEqualTo("1_thumb.jpg");
    }
}
