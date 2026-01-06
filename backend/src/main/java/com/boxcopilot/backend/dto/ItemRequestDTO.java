package com.boxcopilot.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO for creating a new Item.
 */
public class ItemRequestDTO {
    
    @NotBlank(message = "Name is required")
    @Size(min = 1, max = 255, message = "Name must be between 1 and 255 characters")
    private String name;
    
    @NotNull(message = "Box ID is required")
    private Long boxId;

    public ItemRequestDTO() {
    }

    public ItemRequestDTO(String name, Long boxId) {
        this.name = name;
        this.boxId = boxId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getBoxId() {
        return boxId;
    }

    public void setBoxId(Long boxId) {
        this.boxId = boxId;
    }
}
