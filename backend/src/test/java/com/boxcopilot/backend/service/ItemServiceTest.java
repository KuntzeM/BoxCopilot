package com.boxcopilot.backend.service;

import com.boxcopilot.backend.domain.Box;
import com.boxcopilot.backend.domain.Item;
import com.boxcopilot.backend.mapper.ItemMapper;
import com.boxcopilot.backend.repository.BoxRepository;
import com.boxcopilot.backend.repository.ItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Move / bulk move and the image lifecycle (delete, replace) of {@link ItemService}. */
class ItemServiceTest {

    private ItemRepository items;
    private BoxRepository boxes;
    private ImageStorageService storage;
    private ItemService service;

    @BeforeEach
    void setUp() {
        items = mock(ItemRepository.class);
        boxes = mock(BoxRepository.class);
        storage = mock(ImageStorageService.class);
        service = new ItemService(items, boxes, mock(ItemMapper.class), storage);
        when(items.save(any(Item.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Item itemWithImage(long id, String path) {
        Item item = new Item();
        item.setImagePath(path);
        item.setImageUpdatedAt(5L);
        when(items.findById(id)).thenReturn(Optional.of(item));
        return item;
    }

    @Test
    void moveItemReassignsTheBox() {
        Item item = itemWithImage(1L, null);
        Box target = new Box();
        when(boxes.findById(2L)).thenReturn(Optional.of(target));

        service.moveItem(1L, 2L);

        assertThat(item.getBox()).isSameAs(target);
        verify(items).save(item);
    }

    @Test
    void moveItemToMissingBoxChangesNothing() {
        itemWithImage(1L, null);
        when(boxes.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.moveItem(1L, 2L)).isInstanceOf(ResourceNotFoundException.class);

        verify(items, never()).save(any());
    }

    @Test
    void bulkMoveMovesExistingItemsAndSkipsMissingOnes() {
        Item a = itemWithImage(1L, null);
        Item b = itemWithImage(3L, null);
        when(items.findById(2L)).thenReturn(Optional.empty());
        Box target = new Box();
        when(boxes.findById(9L)).thenReturn(Optional.of(target));

        service.moveItems(List.of(1L, 2L, 3L), 9L);

        assertThat(a.getBox()).isSameAs(target);
        assertThat(b.getBox()).isSameAs(target);
        verify(items).save(a);
        verify(items).save(b);
    }

    @Test
    void bulkMoveToMissingBoxMovesNothing() {
        itemWithImage(1L, null);
        when(boxes.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.moveItems(List.of(1L), 9L)).isInstanceOf(ResourceNotFoundException.class);

        verify(items, never()).save(any());
    }

    @Test
    void deleteImageRemovesTheFileAndClearsTheItem() {
        Item item = itemWithImage(1L, "1_thumb.jpg");

        service.deleteImage(1L);

        verify(storage).deleteImage("1_thumb.jpg");
        assertThat(item.getImagePath()).isNull();
        assertThat(item.getImageUpdatedAt()).isNull();
        verify(items).save(item);
    }

    @Test
    void deleteImageWithoutImageTouchesNoFile() {
        Item item = itemWithImage(1L, null);

        service.deleteImage(1L);

        verify(storage, never()).deleteImage(any());
        verify(items, never()).save(item);
    }

    @Test
    void replacingAnImageDeletesTheOldFile() {
        Item item = itemWithImage(1L, "1_old.jpg");
        MockMultipartFile file = new MockMultipartFile("file", "x.png", "image/png", new byte[]{1});
        when(storage.saveImage(1L, file)).thenReturn("1_new.jpg");

        service.uploadImage(1L, file);

        verify(storage).deleteImage("1_old.jpg");
        assertThat(item.getImagePath()).isEqualTo("1_new.jpg");
    }

    @Test
    void replacingWithTheSamePathKeepsTheFile() {
        itemWithImage(1L, "1_same.jpg");
        MockMultipartFile file = new MockMultipartFile("file", "x.png", "image/png", new byte[]{1});
        when(storage.saveImage(1L, file)).thenReturn("1_same.jpg");

        service.uploadImage(1L, file);

        verify(storage, never()).deleteImage(any());
    }
}
