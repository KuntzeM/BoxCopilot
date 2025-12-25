package com.boxcopilot.backend.mapper;

import com.boxcopilot.backend.domain.Item;
import com.boxcopilot.backend.dto.ItemRequestDTO;
import com.boxcopilot.backend.dto.ItemResponseDTO;
import com.boxcopilot.backend.dto.ItemUpdateDTO;
import org.springframework.stereotype.Component;

/**
 * Mapper for Item entity and DTOs.
 */
@Component
public class ItemMapper {

    /**
     * Converts ItemRequestDTO to Item entity (without Box reference).
     * Box must be set separately.
     */
    public Item toEntity(ItemRequestDTO dto) {
        if (dto == null) {
            return null;
        }

        Item item = new Item();
        item.setName(dto.getName());
        return item;
    }

    /**
     * Converts Item entity to ItemResponseDTO.
     */
    public ItemResponseDTO toResponseDTO(Item entity) {
        if (entity == null) {
            return null;
        }

        String boxUuid = null;
        String boxCurrentRoom = null;
        String boxTargetRoom = null;
        Long boxId = null;

        if (entity.getBox() != null) {
            boxUuid = entity.getBox().getUuid();
            boxCurrentRoom = entity.getBox().getCurrentRoom();
            boxTargetRoom = entity.getBox().getTargetRoom();
            boxId = entity.getBox().getId();
        }

        ItemResponseDTO dto = new ItemResponseDTO(
            entity.getId(),
            entity.getName(),
            boxId,
            boxUuid,
            boxCurrentRoom,
            boxTargetRoom
        );
        
        // Set imageUrl if imagePath exists
        if (entity.getImagePath() != null && !entity.getImagePath().isBlank()) {
            dto.setImageUrl("/api/v1/items/" + entity.getId() + "/image");
        }
        
        return dto;
    }

    /**
     * Updates an existing Item entity with values from ItemUpdateDTO.
     * Only updates non-null fields.
     */
    public void updateEntity(Item entity, ItemUpdateDTO dto) {
        if (entity == null || dto == null) {
            return;
        }

        if (dto.getName() != null) {
            entity.setName(dto.getName());
        }
    }
}
