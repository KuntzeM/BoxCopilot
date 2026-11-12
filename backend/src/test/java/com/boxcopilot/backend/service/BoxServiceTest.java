package com.boxcopilot.backend.service;

import com.boxcopilot.backend.domain.Box;
import com.boxcopilot.backend.domain.Item;
import com.boxcopilot.backend.dto.BoxPreviewDTO;
import com.boxcopilot.backend.dto.BoxRequestDTO;
import com.boxcopilot.backend.dto.BoxResponseDTO;
import com.boxcopilot.backend.dto.BoxUpdateDTO;
import com.boxcopilot.backend.repository.BoxRepository;
import com.boxcopilot.backend.mapper.BoxMapper;
import com.boxcopilot.backend.mapper.ItemMapper;
import com.boxcopilot.backend.repository.ItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BoxServiceTest {

    @Autowired
    private BoxService boxService;

    @Autowired
    private BoxRepository boxRepository;

    @Autowired
    private BoxMapper boxMapper;

    @Autowired
    private BoxNumberService boxNumberService;

    @Test
    void testCreateBox_withHandlingFlags() {
        // Given
        BoxRequestDTO request = new BoxRequestDTO();
        request.setCurrentRoom("Küche");
        request.setTargetRoom("Keller");
        request.setDescription("Fragile items");
        request.setIsFragile(true);
        request.setNoStack(true);
        
        // When
        BoxResponseDTO result = boxService.createBox(request);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
        assertThat(result.getCurrentRoom()).isEqualTo("Küche");
        assertThat(result.getTargetRoom()).isEqualTo("Keller");
        assertThat(result.getDescription()).isEqualTo("Fragile items");
        assertThat(result.getIsFragile()).isTrue();
        assertThat(result.getNoStack()).isTrue();
    }

    @Test
    void testCreateBox_withDefaultHandlingFlags() {
        // Given
        BoxRequestDTO request = new BoxRequestDTO();
        request.setCurrentRoom("Wohnzimmer");
        
        // When
        BoxResponseDTO result = boxService.createBox(request);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getIsFragile()).isFalse();
        assertThat(result.getNoStack()).isFalse();
        assertThat(result.getIsMovedToTarget()).isFalse();
        assertThat(result.getLabelPrinted()).isFalse();
    }

    @Test
    void testCreateBox_withStatusFlags() {
        // Given
        BoxRequestDTO request = new BoxRequestDTO();
        request.setCurrentRoom("Büro");
        request.setTargetRoom("Archiv");
        request.setIsMovedToTarget(true);
        request.setLabelPrinted(true);
        
        // When
        BoxResponseDTO result = boxService.createBox(request);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getIsMovedToTarget()).isTrue();
        assertThat(result.getLabelPrinted()).isTrue();
    }

    @Test
    void testUpdateBox_withHandlingFlags() {
        // Given - Create a box first
        BoxRequestDTO createRequest = new BoxRequestDTO();
        createRequest.setCurrentRoom("Schlafzimmer");
        createRequest.setIsFragile(false);
        createRequest.setNoStack(false);
        BoxResponseDTO created = boxService.createBox(createRequest);
        
        // When - Update the box with fragile and noStack flags
        BoxUpdateDTO updateRequest = new BoxUpdateDTO();
        updateRequest.setIsFragile(true);
        updateRequest.setNoStack(true);
        BoxResponseDTO updated = boxService.updateBox(created.getId(), updateRequest);
        
        // Then
        assertThat(updated.getIsFragile()).isTrue();
        assertThat(updated.getNoStack()).isTrue();
        assertThat(updated.getCurrentRoom()).isEqualTo("Schlafzimmer");
    }

    @Test
    void testUpdateBox_partialUpdate() {
        // Given - Create a box with flags
        BoxRequestDTO createRequest = new BoxRequestDTO();
        createRequest.setCurrentRoom("Garage");
        createRequest.setIsFragile(true);
        createRequest.setNoStack(false);
        BoxResponseDTO created = boxService.createBox(createRequest);
        
        // When - Update only noStack flag
        BoxUpdateDTO updateRequest = new BoxUpdateDTO();
        updateRequest.setNoStack(true);
        BoxResponseDTO updated = boxService.updateBox(created.getId(), updateRequest);
        
        // Then
        assertThat(updated.getIsFragile()).isTrue(); // Should remain unchanged
        assertThat(updated.getNoStack()).isTrue();   // Should be updated
    }

    @Test
    void testUpdateBox_withStatusFlags() {
        // Given - Create a box first
        BoxRequestDTO createRequest = new BoxRequestDTO();
        createRequest.setCurrentRoom("Wohnzimmer");
        createRequest.setIsMovedToTarget(false);
        createRequest.setLabelPrinted(false);
        BoxResponseDTO created = boxService.createBox(createRequest);
        
        // When - Update the box with status flags
        BoxUpdateDTO updateRequest = new BoxUpdateDTO();
        updateRequest.setIsMovedToTarget(true);
        updateRequest.setLabelPrinted(true);
        BoxResponseDTO updated = boxService.updateBox(created.getId(), updateRequest);
        
        // Then
        assertThat(updated.getIsMovedToTarget()).isTrue();
        assertThat(updated.getLabelPrinted()).isTrue();
        assertThat(updated.getCurrentRoom()).isEqualTo("Wohnzimmer");
    }

    @Test
    void testUpdateBox_partialStatusFlagUpdate() {
        // Given - Create a box with status flags
        BoxRequestDTO createRequest = new BoxRequestDTO();
        createRequest.setCurrentRoom("Küche");
        createRequest.setIsMovedToTarget(false);
        createRequest.setLabelPrinted(true);
        BoxResponseDTO created = boxService.createBox(createRequest);
        
        // When - Update only isMovedToTarget flag
        BoxUpdateDTO updateRequest = new BoxUpdateDTO();
        updateRequest.setIsMovedToTarget(true);
        BoxResponseDTO updated = boxService.updateBox(created.getId(), updateRequest);
        
        // Then
        assertThat(updated.getIsMovedToTarget()).isTrue();  // Should be updated
        assertThat(updated.getLabelPrinted()).isTrue();     // Should remain unchanged
    }

    @Test
    void testCreateBox_assignsBoxNumber() {
        // Given
        BoxRequestDTO request = new BoxRequestDTO();
        request.setCurrentRoom("Office");
        
        // When
        BoxResponseDTO result = boxService.createBox(request);
        
        // Then
        assertThat(result.getBoxNumber()).isNotNull();
        assertThat(result.getBoxNumber()).isPositive();
    }

    @Test
    void testBoxNumber_isSequentialForNewBoxes() {
        // Given
        BoxRequestDTO request1 = new BoxRequestDTO();
        request1.setCurrentRoom("Room1");
        
        BoxRequestDTO request2 = new BoxRequestDTO();
        request2.setCurrentRoom("Room2");
        
        // When
        BoxResponseDTO box1 = boxService.createBox(request1);
        BoxResponseDTO box2 = boxService.createBox(request2);
        
        // Then
        assertThat(box2.getBoxNumber()).isGreaterThan(box1.getBoxNumber());
    }

    @Test
    void testBoxNumber_isReusedAfterDeletion() {
        // Given - Create 3 boxes
        BoxRequestDTO dto1 = new BoxRequestDTO();
        dto1.setCurrentRoom("Room1");
        BoxResponseDTO box1 = boxService.createBox(dto1);

        BoxRequestDTO dto2 = new BoxRequestDTO();
        dto2.setCurrentRoom("Room2");
        BoxResponseDTO box2 = boxService.createBox(dto2);

        BoxRequestDTO dto3 = new BoxRequestDTO();
        dto3.setCurrentRoom("Room3");
        BoxResponseDTO box3 = boxService.createBox(dto3);

        Integer box2Number = box2.getBoxNumber();

        // When - Delete box 2
        boxService.deleteBox(box2.getId());

        // Create new box - should get number 2
        BoxRequestDTO dto4 = new BoxRequestDTO();
        dto4.setCurrentRoom("Room4");
        BoxResponseDTO box4 = boxService.createBox(dto4);

        // Then - box4 should reuse box2's number
        assertThat(box4.getBoxNumber()).isEqualTo(box2Number);
    }

    // --- image cleanup on delete (plain unit tests; the commit is simulated via the synchronization) ---

    private Box boxWithImages(BoxRepository repo, String... paths) {
        Box box = new Box("u-1", "Cellar", null, null);
        box.setBoxNumber(7);
        for (String path : paths) {
            Item item = new Item(box, "i");
            item.setImagePath(path);
            box.getItems().add(item);
        }
        when(repo.findById(1L)).thenReturn(Optional.of(box));
        return box;
    }

    /** Runs deleteBox and returns only the synchronizations it registered (the test tx has its own). */
    private List<TransactionSynchronization> deleteAndCollect(BoxService service) {
        List<TransactionSynchronization> before = List.copyOf(TransactionSynchronizationManager.getSynchronizations());
        service.deleteBox(1L);
        return TransactionSynchronizationManager.getSynchronizations().stream()
                .filter(sync -> !before.contains(sync)).toList();
    }

    private BoxService unitService(BoxRepository repo, ImageStorageService images) {
        return new BoxService(repo, boxMapper, mock(BoxNumberService.class), mock(ItemRepository.class),
                new ItemMapper(), images);
    }

    @Test
    void deleteBox_removesEachImageFileOnceAfterCommit() {
        BoxRepository repo = mock(BoxRepository.class);
        ImageStorageService images = mock(ImageStorageService.class);
        boxWithImages(repo, "a_thumb.jpg", null, "", "b_thumb.jpg");
        List<TransactionSynchronization> ours = deleteAndCollect(unitService(repo, images));
        verify(images, never()).deleteImage(any());   // nothing before the commit
        ours.forEach(TransactionSynchronization::afterCommit);
        verify(images).deleteImage("a_thumb.jpg");
        verify(images).deleteImage("b_thumb.jpg");
        verify(images, org.mockito.Mockito.times(2)).deleteImage(any());
    }

    @Test
    void deleteBox_keepsImageFilesOnRollback() {
        BoxRepository repo = mock(BoxRepository.class);
        ImageStorageService images = mock(ImageStorageService.class);
        boxWithImages(repo, "a_thumb.jpg");
        List<TransactionSynchronization> ours = deleteAndCollect(unitService(repo, images));
        ours.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(images, never()).deleteImage(any());
    }

    @Test
    void deleteBox_failingFileDeleteIsSwallowedAndDoesNotStopTheRest() {
        BoxRepository repo = mock(BoxRepository.class);
        ImageStorageService images = mock(ImageStorageService.class);
        boxWithImages(repo, "a_thumb.jpg", "b_thumb.jpg");
        doThrow(new com.boxcopilot.backend.exception.FileStorageException("boom")).when(images).deleteImage("a_thumb.jpg");
        List<TransactionSynchronization> ours = deleteAndCollect(unitService(repo, images));
        ours.forEach(TransactionSynchronization::afterCommit);
        verify(images).deleteImage("b_thumb.jpg");
    }

    // --- public preview ---

    @Test
    void getPublicPreview_mapsBoxAndItemsAndHidesMissingImage() {
        BoxResponseDTO created = boxService.createBox(roomRequest("Hall"));
        Box box = boxRepository.findById(created.getId()).orElseThrow();
        Item withImage = new Item(box, "Lamp");
        withImage.setImagePath("x_thumb.jpg");
        withImage.setImageToken("tok");
        withImage.setImageUpdatedAt(99L);
        box.getItems().add(withImage);
        box.getItems().add(new Item(box, "Book"));
        boxRepository.saveAndFlush(box);

        BoxPreviewDTO preview = boxService.getPublicPreview(box.getUuid());

        assertThat(preview.getUuid()).isEqualTo(box.getUuid());
        assertThat(preview.getBoxNumber()).isEqualTo(box.getBoxNumber());
        assertThat(preview.getItems()).extracting(BoxPreviewDTO.ItemDTO::getName).containsExactly("Book", "Lamp");
        assertThat(preview.getItems()).extracting(BoxPreviewDTO.ItemDTO::getImageUrl)
                .containsExactly(null, "/api/v1/public/items/tok/image?t=99");
    }

    @Test
    void getPublicPreview_unknownUuidThrowsNotFound() {
        assertThatThrownBy(() -> boxService.getPublicPreview("nope"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private BoxRequestDTO roomRequest(String room) {
        BoxRequestDTO r = new BoxRequestDTO();
        r.setCurrentRoom(room);
        return r;
    }
}
