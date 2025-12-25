package com.boxcopilot.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * DTO for moving multiple items to another box.
 */
public class BulkMoveItemsDTO {
    
    @NotEmpty(message = "Item IDs list cannot be empty")
    private List<Long> itemIds;
    
    @NotBlank(message = "Target box UUID is required")
    private String targetBoxUuid;

    public BulkMoveItemsDTO() {
    }

    public BulkMoveItemsDTO(List<Long> itemIds, String targetBoxUuid) {
        this.itemIds = itemIds;
        this.targetBoxUuid = targetBoxUuid;
    }

    public List<Long> getItemIds() {
        return itemIds;
    }

    public void setItemIds(List<Long> itemIds) {
        this.itemIds = itemIds;
    }

    public String getTargetBoxUuid() {
        return targetBoxUuid;
    }

    public void setTargetBoxUuid(String targetBoxUuid) {
        this.targetBoxUuid = targetBoxUuid;
    }
}
