package com.boxcopilot.backend.controller;

import com.boxcopilot.backend.dto.BoxRequestDTO;
import com.boxcopilot.backend.dto.BoxResponseDTO;
import com.boxcopilot.backend.dto.BoxUpdateDTO;
import com.boxcopilot.backend.service.BoxService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for Box operations.
 * Handles HTTP requests and delegates business logic to BoxService.
 * All endpoints require authentication via OIDC.
 */
@RestController
@RequestMapping("/api/v1/boxes")
@PreAuthorize("isAuthenticated()")
public class BoxController {
    
    private static final Logger log = LoggerFactory.getLogger(BoxController.class);
    private final BoxService boxService;

    public BoxController(BoxService boxService) {
        this.boxService = boxService;
    }

    /**
     * Lists all boxes.
     */
    @GetMapping
    public ResponseEntity<List<BoxResponseDTO>> list() {
        log.debug("Fetching all boxes");
        List<BoxResponseDTO> boxes = boxService.getAllBoxes();
        log.info("Retrieved {} boxes", boxes.size());
        return ResponseEntity.ok(boxes);
    }

    /**
     * Retrieves a box by UUID.
     */
    @GetMapping("/{uuid}")
    public ResponseEntity<BoxResponseDTO> getByUuid(@PathVariable String uuid) {
        log.debug("Fetching box with UUID: {}", uuid);
        BoxResponseDTO box = boxService.getBoxByUuid(uuid);
        log.info("Retrieved box: {}", box.getUuid());
        return ResponseEntity.ok(box);
    }

    /**
     * Creates a new box.
     */
    @PostMapping
    public ResponseEntity<BoxResponseDTO> create(@Valid @RequestBody BoxRequestDTO requestDTO) {
        log.info("Creating new box in room: {}", requestDTO.getCurrentRoom());
        BoxResponseDTO createdBox = boxService.createBox(requestDTO);
        log.info("Box created successfully with UUID: {}", createdBox.getUuid());
        return ResponseEntity.status(HttpStatus.CREATED).body(createdBox);
    }

    /**
     * Updates an existing box.
     */
    @PutMapping("/{id}")
    public ResponseEntity<BoxResponseDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody BoxUpdateDTO updateDTO) {
        log.info("Updating box with ID: {}", id);
        BoxResponseDTO updatedBox = boxService.updateBox(id, updateDTO);
        log.info("Box updated successfully: {}", updatedBox.getUuid());
        return ResponseEntity.ok(updatedBox);
    }

    /**
     * Deletes a box.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("Deleting box with ID: {}", id);
        boxService.deleteBox(id);
        log.info("Box with ID {} deleted successfully", id);
        return ResponseEntity.noContent().build();
    }
}
