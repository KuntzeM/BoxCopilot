package com.boxcopilot.backend.dto;

import jakarta.validation.constraints.NotNull;

/**
 * DTO for moving a single item to another box.
 */
public class MoveItemDTO {
    
    @NotNull(message = "Target box ID is required")
    private Long targetBoxId;

    public MoveItemDTO() {
    }

    public MoveItemDTO(Long targetBoxId) {
        this.targetBoxId = targetBoxId;
    }

    public Long getTargetBoxId() {
        return targetBoxId;
    }

    public void setTargetBoxId(Long targetBoxId) {
        this.targetBoxId = targetBoxId;
    }
}
