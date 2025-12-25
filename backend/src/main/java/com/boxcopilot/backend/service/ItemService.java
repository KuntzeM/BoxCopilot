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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
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

    public ItemService(ItemRepository itemRepository, BoxRepository boxRepository, ItemMapper itemMapper) {
        this.itemRepository = itemRepository;
        this.boxRepository = boxRepository;
        this.itemMapper = itemMapper;
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
        
        List<ItemResponseDTO> items = itemRepository.findAll().stream()
            .filter(item -> item.getBox().equals(box))
            .sorted(Comparator.comparing(Item::getName, String.CASE_INSENSITIVE_ORDER))
            .map(itemMapper::toResponseDTO)
            .collect(Collectors.toList());
        log.debug("Service: Found {} items for box: {}", items.size(), boxUuid);
        return items;
    }

    /**
     * Creates a new item.
     */
    public ItemResponseDTO createItem(ItemRequestDTO requestDTO) {
        log.info("Service: Creating item '{}' in box: {}", requestDTO.getName(), requestDTO.getBoxUuid());
        Box box = boxRepository.findByUuid(requestDTO.getBoxUuid())
            .orElseThrow(() -> {
                log.error("Cannot create item - Box not found with UUID: {}", requestDTO.getBoxUuid());
                return new ResourceNotFoundException("Box not found with UUID: " + requestDTO.getBoxUuid());
            });

        Item item = itemMapper.toEntity(requestDTO);
        item.setBox(box);
        
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
     */
    public void deleteItem(Long id) {
        log.info("Service: Deleting item with ID: {}", id);
        if (!itemRepository.existsById(id)) {
            log.error("Cannot delete - Item not found with ID: {}", id);
            throw new ResourceNotFoundException("Item not found with ID: " + id);
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
}
