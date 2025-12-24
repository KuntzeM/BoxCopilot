package com.boxcopilot.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for creating a new Item.
 */
public class ItemRequestDTO {
    
    @NotBlank(message = "Name is required")
    @Size(min = 1, max = 255, message = "Name must be between 1 and 255 characters")
    private String name;
    
    @NotBlank(message = "Box UUID is required")
    private String boxUuid;

    public ItemRequestDTO() {
    }

    public ItemRequestDTO(String name, String boxUuid) {
        this.name = name;
        this.boxUuid = boxUuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBoxUuid() {
        return boxUuid;
    }

    public void setBoxUuid(String boxUuid) {
        this.boxUuid = boxUuid;
    }
}
