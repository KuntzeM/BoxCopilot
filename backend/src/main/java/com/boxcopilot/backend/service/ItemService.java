package com.boxcopilot.backend.service;

import com.boxcopilot.backend.domain.Box;
import com.boxcopilot.backend.domain.Item;
import com.boxcopilot.backend.dto.ItemRequestDTO;
import com.boxcopilot.backend.dto.ItemResponseDTO;
import com.boxcopilot.backend.dto.ItemUpdateDTO;
import com.boxcopilot.backend.mapper.ItemMapper;
import com.boxcopilot.backend.repository.BoxRepository;
import com.boxcopilot.backend.repository.ItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service layer for Item business logic.
 */
@Service
@Transactional
public class ItemService {

    private static final Logger log = LoggerFactory.getLogger(ItemService.class);

    private final ItemRepository itemRepository;
    private final BoxRepository boxRepository;
    private final ItemMapper itemMapper;
    private final ImageStorageService imageStorageService;

    public ItemService(ItemRepository itemRepository, BoxRepository boxRepository, 
                      ItemMapper itemMapper, ImageStorageService imageStorageService) {
        this.itemRepository = itemRepository;
        this.boxRepository = boxRepository;
        this.itemMapper = itemMapper;
        this.imageStorageService = imageStorageService;
    }

    @PostConstruct
    public void initImageTokens() {
        ensureImageTokens();
    }

    /**
     * Retrieves all items.
     */
    @Transactional(readOnly = true)
    public List<ItemResponseDTO> getAllItems() {
        log.debug("Service: Fetching all items");
        List<ItemResponseDTO> items = itemRepository.findAll().stream()
            .sorted(Comparator.comparing(Item::getName, String.CASE_INSENSITIVE_ORDER))
            .map(itemMapper::toResponseDTO)
            .collect(Collectors.toList());
        log.debug("Service: Found {} items", items.size());
        return items;
    }

    /**
     * Retrieves items by box UUID.
     */
    @Transactional(readOnly = true)
    public List<ItemResponseDTO> getItemsByBoxUuid(String boxUuid) {
        log.debug("Service: Fetching items for box UUID: {}", boxUuid);
        Box box = boxRepository.findByUuid(boxUuid)
            .orElseThrow(() -> {
                log.error("Box not found with UUID: {}", boxUuid);
                return new ResourceNotFoundException("Box not found with UUID: " + boxUuid);
            });
        List<ItemResponseDTO> items = itemRepository.findByBox_UuidOrderByNameAsc(box.getUuid()).stream()
            .map(itemMapper::toResponseDTO)
            .collect(Collectors.toList());
        log.debug("Service: Found {} items for box: {}", items.size(), boxUuid);
        return items;
    }

    /**
     * Creates a new item.
     */
    public ItemResponseDTO createItem(ItemRequestDTO requestDTO) {
        log.info("Service: Creating item '{}' in box ID: {}", requestDTO.getName(), requestDTO.getBoxId());
        Box box = boxRepository.findById(requestDTO.getBoxId())
            .orElseThrow(() -> {
                log.error("Cannot create item - Box not found with ID: {}", requestDTO.getBoxId());
                return new ResourceNotFoundException("Box not found with ID: " + requestDTO.getBoxId());
            });

        Item item = itemMapper.toEntity(requestDTO);
        item.setBox(box);
        if (item.getImageToken() == null || item.getImageToken().isBlank()) {
            item.setImageToken(UUID.randomUUID().toString());
        }
        
        Item savedItem = itemRepository.save(item);
        log.info("Service: Item created with ID: {}, Name: {}", savedItem.getId(), savedItem.getName());
        return itemMapper.toResponseDTO(savedItem);
    }

    /**
     * Updates an existing item.
     */
    public ItemResponseDTO updateItem(Long id, ItemUpdateDTO updateDTO) {
        log.info("Service: Updating item with ID: {}", id);
        Item item = itemRepository.findById(id)
            .orElseThrow(() -> {
                log.error("Cannot update - Item not found with ID: {}", id);
                return new ResourceNotFoundException("Item not found with ID: " + id);
            });
        
        itemMapper.updateEntity(item, updateDTO);
        Item updatedItem = itemRepository.save(item);
        log.info("Service: Item updated - ID: {}, Name: {}", updatedItem.getId(), updatedItem.getName());
        return itemMapper.toResponseDTO(updatedItem);
    }

    /**
     * Deletes an item by ID.
     * Also deletes associated image if present.
     */
    public void deleteItem(Long id) {
        log.info("Service: Deleting item with ID: {}", id);
        Item item = itemRepository.findById(id)
            .orElseThrow(() -> {
                log.error("Cannot delete - Item not found with ID: {}", id);
                return new ResourceNotFoundException("Item not found with ID: " + id);
            });
        
        // Delete associated image if exists
        if (item.getImagePath() != null && !item.getImagePath().isBlank()) {
            try {
                imageStorageService.deleteImage(item.getImagePath());
                log.info("Service: Deleted image for item ID: {}", id);
            } catch (Exception e) {
                log.warn("Service: Failed to delete image for item ID: {}, continuing with item deletion", id, e);
            }
        }
        
        itemRepository.deleteById(id);
        log.info("Service: Item with ID {} deleted successfully", id);
    }

    /**
     * Searches items by partial name. Optionally filters by box UUID.
     */
    @Transactional(readOnly = true)
    public List<ItemResponseDTO> searchItems(String query, String boxUuid) {
        if (query == null || query.isBlank()) {
            log.debug("Service: Empty search query, returning empty list");
            return List.of();
        }

        log.debug("Service: Searching items with query: '{}', boxUuid: {}", query, boxUuid);
        List<Item> items;
        if (boxUuid != null && !boxUuid.isBlank()) {
            items = itemRepository.findByBox_UuidAndNameContainingIgnoreCaseOrderByNameAsc(boxUuid, query);
        } else {
            items = itemRepository.findByNameContainingIgnoreCaseOrderByNameAsc(query);
        }

        List<ItemResponseDTO> result = items.stream()
            .map(itemMapper::toResponseDTO)
            .collect(Collectors.toList());
        log.debug("Service: Search found {} items", result.size());
        return result;
    }
    
    /**
     * Uploads and saves an image for an item.
     */
    public ItemResponseDTO uploadImage(Long itemId, MultipartFile file) {
        log.info("Service: Uploading image for item ID: {}", itemId);
        Item item = itemRepository.findById(itemId)
            .orElseThrow(() -> {
                log.error("Cannot upload image - Item not found with ID: {}", itemId);
                return new ResourceNotFoundException("Item not found with ID: " + itemId);
            });
        
        // Delete old image if exists
        if (item.getImagePath() != null && !item.getImagePath().isBlank()) {
            try {
                imageStorageService.deleteImage(item.getImagePath());
                log.debug("Service: Deleted old image for item ID: {}", itemId);
            } catch (Exception e) {
                log.warn("Service: Failed to delete old image for item ID: {}", itemId, e);
            }
        }
        
        // Save new image
        String imagePath = imageStorageService.saveImage(itemId, file);
        item.setImagePath(imagePath);
        item.setImageUpdatedAt(System.currentTimeMillis());
        Item savedItem = itemRepository.save(item);
        
        log.info("Service: Image uploaded successfully for item ID: {}", itemId);
        return itemMapper.toResponseDTO(savedItem);
    }
    
    /**
     * Deletes the image associated with an item (requires authentication).
     */
    public ItemResponseDTO deleteImage(Long itemId) {
        log.info("Service: Deleting image for item ID: {}", itemId);
        Item item = itemRepository.findById(itemId)
            .orElseThrow(() -> {
                log.error("Cannot delete image - Item not found with ID: {}", itemId);
                return new ResourceNotFoundException("Item not found with ID: " + itemId);
            });
        
        if (item.getImagePath() != null && !item.getImagePath().isBlank()) {
            imageStorageService.deleteImage(item.getImagePath());
            item.setImagePath(null);
            item.setImageUpdatedAt(null);
            Item savedItem = itemRepository.save(item);
            log.info("Service: Image deleted successfully for item ID: {}", itemId);
            return itemMapper.toResponseDTO(savedItem);
        } else {
            log.debug("Service: No image to delete for item ID: {}", itemId);
            return itemMapper.toResponseDTO(item);
        }
    }
    
    /**
     * Moves an item to a different box.
     */
    public ItemResponseDTO moveItem(Long itemId, Long targetBoxId) {
        log.info("Service: Moving item ID: {} to box ID: {}", itemId, targetBoxId);
        Item item = itemRepository.findById(itemId)
            .orElseThrow(() -> {
                log.error("Cannot move - Item not found with ID: {}", itemId);
                return new ResourceNotFoundException("Item not found with ID: " + itemId);
            });
        
        Box targetBox = boxRepository.findById(targetBoxId)
            .orElseThrow(() -> {
                log.error("Cannot move - Target box not found with ID: {}", targetBoxId);
                return new ResourceNotFoundException("Box not found with ID: " + targetBoxId);
            });
        
        item.setBox(targetBox);
        Item savedItem = itemRepository.save(item);
        log.info("Service: Item ID: {} moved to box ID: {}", itemId, targetBoxId);
        return itemMapper.toResponseDTO(savedItem);
    }
    
    /**
     * Moves multiple items to a different box.
     */
    public void moveItems(List<Long> itemIds, Long targetBoxId) {
        log.info("Service: Moving {} items to box ID: {}", itemIds.size(), targetBoxId);
        Box targetBox = boxRepository.findById(targetBoxId)
            .orElseThrow(() -> {
                log.error("Cannot move - Target box not found with ID: {}", targetBoxId);
                return new ResourceNotFoundException("Box not found with ID: " + targetBoxId);
            });
        
        int movedCount = 0;
        for (Long itemId : itemIds) {
            try {
                Item item = itemRepository.findById(itemId)
                    .orElseThrow(() -> new ResourceNotFoundException("Item not found with ID: " + itemId));
                item.setBox(targetBox);
                itemRepository.save(item);
                movedCount++;
            } catch (Exception e) {
                log.warn("Service: Failed to move item ID: {}", itemId, e);
            }
        }
        log.info("Service: Successfully moved {} out of {} items to box ID: {}", movedCount, itemIds.size(), targetBoxId);
    }

    /**
     * Retrieve thumbnail image by opaque token for public access.
     */
    @Transactional(readOnly = true)
    public Resource getImageByToken(String token) {
        Item item = itemRepository.findByImageToken(token)
            .orElseThrow(() -> new ResourceNotFoundException("Item not found for image token"));
        return imageStorageService.getImage(item.getId());
    }

    /**
     * Retrieve large image by opaque token for public access.
     */
    @Transactional(readOnly = true)
    public Resource getLargeImageByToken(String token) {
        Item item = itemRepository.findByImageToken(token)
            .orElseThrow(() -> new ResourceNotFoundException("Item not found for image token"));
        return imageStorageService.getLargeImage(item.getId());
    }

    @Transactional(readOnly = true)
    public long getImageLastModifiedByToken(String token) {
        Item item = itemRepository.findByImageToken(token)
            .orElseThrow(() -> new ResourceNotFoundException("Item not found for image token"));
        return imageStorageService.getImageLastModified(item.getId());
    }

    @Transactional(readOnly = true)
    public long getLargeImageLastModifiedByToken(String token) {
        Item item = itemRepository.findByImageToken(token)
            .orElseThrow(() -> new ResourceNotFoundException("Item not found for image token"));
        return imageStorageService.getLargeImageLastModified(item.getId());
    }

    /**
     * Backfill missing image tokens (for legacy items without tokens).
     */
    @Transactional
    public void ensureImageTokens() {
        List<Item> items = itemRepository.findAll().stream()
            .filter(i -> i.getImageToken() == null || i.getImageToken().isBlank())
            .toList();
        if (items.isEmpty()) {
            return;
        }
        for (Item item : items) {
            item.setImageToken(UUID.randomUUID().toString());
        }
        itemRepository.saveAll(items);
        log.info("Backfilled image tokens for {} items", items.size());
    }
}

