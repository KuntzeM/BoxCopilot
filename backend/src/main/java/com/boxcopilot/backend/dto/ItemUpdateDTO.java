package com.boxcopilot.backend.dto;

import jakarta.validation.constraints.Size;

/**
 * DTO for updating an existing Item.
 * All fields are optional.
 */
public class ItemUpdateDTO {
    
    @Size(min = 1, max = 255, message = "Name must be between 1 and 255 characters")
    private String name;

    public ItemUpdateDTO() {
    }

    public ItemUpdateDTO(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
