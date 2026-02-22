package com.boxcopilot.backend.mapper;

import com.boxcopilot.backend.domain.Box;
import com.boxcopilot.backend.domain.Item;
import com.boxcopilot.backend.dto.BoxRequestDTO;
import com.boxcopilot.backend.dto.BoxResponseDTO;
import com.boxcopilot.backend.dto.BoxUpdateDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Mapper for Box entity and DTOs.
 * Centralizes conversion logic for better maintainability.
 */
@Component
public class BoxMapper {

    private final ItemMapper itemMapper;

    public BoxMapper(ItemMapper itemMapper) {
        this.itemMapper = itemMapper;
    }

    /**
     * Converts BoxRequestDTO to Box entity.
     */
    public Box toEntity(BoxRequestDTO dto) {
        if (dto == null) {
            return null;
        }

        Box box = new Box();
        box.setUuid(UUID.randomUUID().toString());
        box.setCurrentRoom(dto.getCurrentRoom());
        box.setTargetRoom(dto.getTargetRoom());
        box.setDescription(dto.getDescription());
        box.setIsFragile(dto.getIsFragile() != null ? dto.getIsFragile() : false);
        box.setNoStack(dto.getNoStack() != null ? dto.getNoStack() : false);
        box.setIsMovedToTarget(dto.getIsMovedToTarget() != null ? dto.getIsMovedToTarget() : false);
        box.setLabelPrinted(dto.getLabelPrinted() != null ? dto.getLabelPrinted() : false);
        return box;
    }

    /**
     * Converts Box entity to BoxResponseDTO.
     */
    public BoxResponseDTO toResponseDTO(Box entity) {
        if (entity == null) {
            return null;
        }

        BoxResponseDTO dto = new BoxResponseDTO(
            entity.getId(),
            entity.getUuid(),
            entity.getCurrentRoom(),
            entity.getTargetRoom(),
            entity.getDescription(),
            entity.getCreatedAt()
        );
        
        dto.setBoxNumber(entity.getBoxNumber());
        dto.setIsFragile(entity.getIsFragile());
        dto.setNoStack(entity.getNoStack());
        dto.setIsMovedToTarget(entity.getIsMovedToTarget());
        dto.setLabelPrinted(entity.getLabelPrinted());

        // Add items if they exist (sorted alphabetically), otherwise use empty list
        if (entity.getItems() != null && !entity.getItems().isEmpty()) {
            dto.setItems(entity.getItems().stream()
                .sorted(Comparator.comparing(Item::getName, String.CASE_INSENSITIVE_ORDER))
                .map(itemMapper::toResponseDTO)
                .collect(Collectors.toList()));
        } else {
            // Ensure items is never null - set empty list if no items exist
            dto.setItems(new ArrayList<>());
        }

        return dto;
    }

    /**
     * Updates an existing Box entity with values from BoxUpdateDTO.
     * Only updates non-null fields.
     */
    public void updateEntity(Box entity, BoxUpdateDTO dto) {
        if (entity == null || dto == null) {
            return;
        }

        if (dto.getCurrentRoom() != null) {
            entity.setCurrentRoom(dto.getCurrentRoom());
        }
        if (dto.getTargetRoom() != null) {
            entity.setTargetRoom(dto.getTargetRoom());
        }
        if (dto.getDescription() != null) {
            entity.setDescription(dto.getDescription());
        }
        if (dto.getIsFragile() != null) {
            entity.setIsFragile(dto.getIsFragile());
        }
        if (dto.getNoStack() != null) {
            entity.setNoStack(dto.getNoStack());
        }
        if (dto.getIsMovedToTarget() != null) {
            entity.setIsMovedToTarget(dto.getIsMovedToTarget());
        }
        if (dto.getLabelPrinted() != null) {
            entity.setLabelPrinted(dto.getLabelPrinted());
        }
    }
}
