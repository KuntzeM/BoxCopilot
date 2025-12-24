package com.boxcopilot.backend.mapper;

import com.boxcopilot.backend.domain.Box;
import com.boxcopilot.backend.dto.BoxRequestDTO;
import com.boxcopilot.backend.dto.BoxResponseDTO;
import com.boxcopilot.backend.dto.BoxUpdateDTO;
import org.springframework.stereotype.Component;

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

        // Add items if they exist
        if (entity.getItems() != null && !entity.getItems().isEmpty()) {
            dto.setItems(entity.getItems().stream()
                .map(itemMapper::toResponseDTO)
                .collect(Collectors.toList()));
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
    }
}
