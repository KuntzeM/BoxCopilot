package com.boxcopilot.backend.service;

import com.boxcopilot.backend.domain.Box;
import com.boxcopilot.backend.domain.Item;
import com.boxcopilot.backend.dto.BoxPreviewDTO;
import com.boxcopilot.backend.dto.BoxRequestDTO;
import com.boxcopilot.backend.dto.BoxResponseDTO;
import com.boxcopilot.backend.dto.BoxUpdateDTO;
import com.boxcopilot.backend.mapper.BoxMapper;
import com.boxcopilot.backend.mapper.ItemMapper;
import com.boxcopilot.backend.repository.BoxRepository;
import com.boxcopilot.backend.repository.ItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service layer for Box business logic.
 * Encapsulates all Box-related operations.
 */
@Service
@Transactional
public class BoxService {

    private static final Logger log = LoggerFactory.getLogger(BoxService.class);

    private final BoxRepository boxRepository;
    private final BoxMapper boxMapper;
    private final BoxNumberService boxNumberService;
    private final ItemRepository itemRepository;
    private final ItemMapper itemMapper;
    private final ImageStorageService imageStorageService;

    public BoxService(BoxRepository boxRepository, BoxMapper boxMapper, BoxNumberService boxNumberService,
                      ItemRepository itemRepository, ItemMapper itemMapper, ImageStorageService imageStorageService) {
        this.boxRepository = boxRepository;
        this.boxMapper = boxMapper;
        this.boxNumberService = boxNumberService;
        this.itemRepository = itemRepository;
        this.itemMapper = itemMapper;
        this.imageStorageService = imageStorageService;
    }

    /**
     * Unauthenticated preview of a box by its UUID. Throws {@link ResourceNotFoundException} (404) when unknown.
     */
    @Transactional(readOnly = true)
    public BoxPreviewDTO getPublicPreview(String uuid) {
        Box box = boxRepository.findByUuid(uuid)
            .orElseThrow(() -> new ResourceNotFoundException("Box not found"));
        List<BoxPreviewDTO.ItemDTO> items = itemRepository.findByBoxIdOrderByNameAsc(box.getId()).stream()
            .map(i -> {
                BoxPreviewDTO.ItemDTO dto = new BoxPreviewDTO.ItemDTO(i.getId(), i.getName());
                dto.setImageUrl(itemMapper.publicImageUrl(i));
                return dto;
            })
            .collect(Collectors.toList());
        BoxPreviewDTO dto = new BoxPreviewDTO(
            box.getId(), box.getUuid(), box.getCurrentRoom(), box.getTargetRoom(), box.getDescription(), items);
        dto.setBoxNumber(box.getBoxNumber());
        dto.setIsFragile(box.getIsFragile());
        dto.setNoStack(box.getNoStack());
        return dto;
    }

    /**
     * Retrieves all boxes with their items.
     */
    @Transactional(readOnly = true)
    public List<BoxResponseDTO> getAllBoxes(boolean includeItems) {
        log.debug("Service: Fetching all boxes (includeItems={})", includeItems);
        List<BoxResponseDTO> boxes = boxRepository.findAllByOrderByBoxNumberDescIdDesc().stream()
            .map(includeItems ? boxMapper::toResponseDTO : boxMapper::toSummaryResponseDTO)
            .collect(Collectors.toList());
        log.debug("Service: Found {} boxes", boxes.size());
        return boxes;
    }

    /**
     * Finds a box by UUID.
     */
    @Transactional(readOnly = true)
    public BoxResponseDTO getBoxByUuid(String uuid) {
        log.debug("Service: Finding box by UUID: {}", uuid);
        Box box = boxRepository.findByUuid(uuid)
            .orElseThrow(() -> {
                log.error("Box not found with UUID: {}", uuid);
                return new ResourceNotFoundException("Box not found with UUID: " + uuid);
            });
        return boxMapper.toResponseDTO(box);
    }

    /**
     * Finds a box by ID.
     */
    @Transactional(readOnly = true)
    public BoxResponseDTO getBoxById(Long id) {
        log.debug("Service: Finding box by ID: {}", id);
        Box box = boxRepository.findById(id)
            .orElseThrow(() -> {
                log.error("Box not found with ID: {}", id);
                return new ResourceNotFoundException("Box not found with ID: " + id);
            });
        return boxMapper.toResponseDTO(box);
    }

    /**
     * Creates a new box.
     */
    public BoxResponseDTO createBox(BoxRequestDTO requestDTO) {
        log.info("Service: Creating box in room: {}", requestDTO.getCurrentRoom());
        Box box = boxMapper.toEntity(requestDTO);
        
        // Box-Nummer aus Pool holen
        Integer boxNumber = boxNumberService.getNextAvailableBoxNumber();
        box.setBoxNumber(boxNumber);
        
        Box savedBox = boxRepository.save(box);
        log.info("Service: Box created with ID: {}, UUID: {}, Number: {}", savedBox.getId(), savedBox.getUuid(), boxNumber);
        return boxMapper.toResponseDTO(savedBox);
    }

    /**
     * Updates an existing box by ID.
     */
    public BoxResponseDTO updateBox(Long id, BoxUpdateDTO updateDTO) {
        log.info("Service: Updating box with ID: {}", id);
        Box box = boxRepository.findById(id)
            .orElseThrow(() -> {
                log.error("Cannot update - Box not found with ID: {}", id);
                return new ResourceNotFoundException("Box not found with ID: " + id);
            });
        
        boxMapper.updateEntity(box, updateDTO);
        Box updatedBox = boxRepository.save(box);
        log.info("Service: Box updated - ID: {}, UUID: {}", updatedBox.getId(), updatedBox.getUuid());
        return boxMapper.toResponseDTO(updatedBox);
    }

    /**
     * Revokes all public links of a box in one transaction: new box UUID and a new image token for every item
     * that has one. Token is part of the image URL, so the new URL is a new cache key (no imageUpdatedAt change).
     */
    public BoxResponseDTO rotateShareLinks(Long id) {
        Box box = boxRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Box not found with ID: " + id));
        box.setUuid(UUID.randomUUID().toString());
        for (Item item : itemRepository.findByBoxIdOrderByNameAsc(id)) {
            if (item.getImageToken() != null) {
                item.setImageToken(UUID.randomUUID().toString());
            }
        }
        log.info("Service: Share links rotated for box ID: {}", id);
        return boxMapper.toResponseDTO(boxRepository.saveAndFlush(box));
    }

    /**
     * Deletes a box by ID.
     */
    public void deleteBox(Long id) {
        log.info("Service: Deleting box with ID: {}", id);
        Box box = boxRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Cannot delete - Box not found with ID: {}", id);
                    return new ResourceNotFoundException("Box not found with ID: " + id);
                });
        
        Integer boxNumber = box.getBoxNumber();
        List<String> imagePaths = box.getItems().stream()
            .map(Item::getImagePath)
            .filter(p -> p != null && !p.isBlank())
            .toList();
        boxRepository.deleteById(id);
        deleteImagesAfterCommit(imagePaths);
        
        // Box-Nummer zurück in den Pool geben
        boxNumberService.releaseBoxNumber(boxNumber);
        log.info("Service: Box with ID {} deleted successfully and number {} released", id, boxNumber);
    }

    /** Files are removed only once the delete committed; a failing file delete is logged, never rethrown. */
    private void deleteImagesAfterCommit(List<String> imagePaths) {
        if (imagePaths.isEmpty()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (String path : imagePaths) {
                    try {
                        imageStorageService.deleteImage(path);
                    } catch (RuntimeException e) {
                        log.warn("Could not delete image file {} of a deleted box", path, e);
                    }
                }
            }
        });
    }
}
