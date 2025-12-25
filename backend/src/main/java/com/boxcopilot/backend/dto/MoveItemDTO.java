package com.boxcopilot.backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO for moving a single item to another box.
 */
public class MoveItemDTO {
    
    @NotBlank(message = "Target box UUID is required")
    private String targetBoxUuid;

    public MoveItemDTO() {
    }

    public MoveItemDTO(String targetBoxUuid) {
        this.targetBoxUuid = targetBoxUuid;
    }

    public String getTargetBoxUuid() {
        return targetBoxUuid;
    }

    public void setTargetBoxUuid(String targetBoxUuid) {
        this.targetBoxUuid = targetBoxUuid;
    }
}
