package com.boxcopilot.backend.controller;

import com.boxcopilot.backend.dto.ItemRequestDTO;
import com.boxcopilot.backend.dto.ItemResponseDTO;
import com.boxcopilot.backend.dto.ItemUpdateDTO;
import com.boxcopilot.backend.service.ItemService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for Item operations.
 * All endpoints require authentication via OIDC.
 */
@RestController
@RequestMapping("/api/v1/items")
@PreAuthorize("isAuthenticated()")
public class ItemController {

    private static final Logger log = LoggerFactory.getLogger(ItemController.class);
    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    /**
     * Lists all items.
     */
    @GetMapping
    public ResponseEntity<List<ItemResponseDTO>> list() {
        log.debug("Fetching all items");
        List<ItemResponseDTO> items = itemService.getAllItems();
        log.info("Retrieved {} items", items.size());
        return ResponseEntity.ok(items);
    }

    /**
     * Lists items by box UUID.
     */
    @GetMapping("/box/{boxUuid}")
    public ResponseEntity<List<ItemResponseDTO>> listByBox(@PathVariable String boxUuid) {
        log.debug("Fetching items for box UUID: {}", boxUuid);
        List<ItemResponseDTO> items = itemService.getItemsByBoxUuid(boxUuid);
        log.info("Retrieved {} items for box: {}", items.size(), boxUuid);
        return ResponseEntity.ok(items);
    }

    /**
     * Search items by name with optional box filter.
     * Example: /api/v1/items/search?q=hammer or /api/v1/items/search?q=hammer&boxUuid=...
     */
    @GetMapping("/search")
    public ResponseEntity<List<ItemResponseDTO>> search(
            @RequestParam String q,
            @RequestParam(required = false) String boxUuid) {
        log.debug("Searching items with query: '{}', boxUuid: {}", q, boxUuid);
        List<ItemResponseDTO> items = itemService.searchItems(q, boxUuid);
        log.info("Search found {} items for query: '{}'", items.size(), q);
        return ResponseEntity.ok(items);
    }

    /**
     * Creates a new item.
     */
    @PostMapping
    public ResponseEntity<ItemResponseDTO> create(@Valid @RequestBody ItemRequestDTO requestDTO) {
        log.info("Creating new item: {} in box: {}", requestDTO.getName(), requestDTO.getBoxUuid());
        ItemResponseDTO createdItem = itemService.createItem(requestDTO);
        log.info("Item created successfully with ID: {}", createdItem.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(createdItem);
    }

    /**
     * Updates an existing item.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ItemResponseDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody ItemUpdateDTO updateDTO) {
        log.info("Updating item with ID: {}", id);
        ItemResponseDTO updatedItem = itemService.updateItem(id, updateDTO);
        log.info("Item updated successfully: {}", updatedItem.getName());
        return ResponseEntity.ok(updatedItem);
    }

    /**
     * Deletes an item.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("Deleting item with ID: {}", id);
        itemService.deleteItem(id);
        log.info("Item with ID {} deleted successfully", id);
        return ResponseEntity.noContent().build();
    }
}
