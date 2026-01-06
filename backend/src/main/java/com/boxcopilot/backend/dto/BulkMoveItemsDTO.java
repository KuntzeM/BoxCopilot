package com.boxcopilot.backend.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * DTO for moving multiple items to another box.
 */
public class BulkMoveItemsDTO {
    
    @NotEmpty(message = "Item IDs list cannot be empty")
    private List<Long> itemIds;
    
    @NotNull(message = "Target box ID is required")
    private Long targetBoxId;

    public BulkMoveItemsDTO() {
    }

    public BulkMoveItemsDTO(List<Long> itemIds, Long targetBoxId) {
        this.itemIds = itemIds;
        this.targetBoxId = targetBoxId;
    }

    public List<Long> getItemIds() {
        return itemIds;
    }

    public void setItemIds(List<Long> itemIds) {
        this.itemIds = itemIds;
    }

    public Long getTargetBoxId() {
        return targetBoxId;
    }

    public void setTargetBoxId(Long targetBoxId) {
        this.targetBoxId = targetBoxId;
    }
}
