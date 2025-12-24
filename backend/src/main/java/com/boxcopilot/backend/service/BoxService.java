package com.boxcopilot.backend.service;

import com.boxcopilot.backend.domain.Box;
import com.boxcopilot.backend.dto.BoxRequestDTO;
import com.boxcopilot.backend.dto.BoxResponseDTO;
import com.boxcopilot.backend.dto.BoxUpdateDTO;
import com.boxcopilot.backend.mapper.BoxMapper;
import com.boxcopilot.backend.repository.BoxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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

    public BoxService(BoxRepository boxRepository, BoxMapper boxMapper) {
        this.boxRepository = boxRepository;
        this.boxMapper = boxMapper;
    }

    /**
     * Retrieves all boxes with their items.
     */
    @Transactional(readOnly = true)
    public List<BoxResponseDTO> getAllBoxes() {
        log.debug("Service: Fetching all boxes");
        List<BoxResponseDTO> boxes = boxRepository.findAll().stream()
            .map(boxMapper::toResponseDTO)
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
        Box savedBox = boxRepository.save(box);
        log.info("Service: Box created with ID: {}, UUID: {}", savedBox.getId(), savedBox.getUuid());
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
     * Deletes a box by ID.
     */
    public void deleteBox(Long id) {
        log.info("Service: Deleting box with ID: {}", id);
        if (!boxRepository.existsById(id)) {
            log.error("Cannot delete - Box not found with ID: {}", id);
            throw new ResourceNotFoundException("Box not found with ID: " + id);
        }
        boxRepository.deleteById(id);
        log.info("Service: Box with ID {} deleted successfully", id);
    }
}
