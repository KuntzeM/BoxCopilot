package com.boxcopilot.backend.dto;

import jakarta.validation.constraints.Size;

/**
 * DTO for creating a new Box.
 * Ensures clean separation between API layer and domain model.
 */
public class BoxRequestDTO {
    
    @Size(max = 255, message = "Current room must be at most 255 characters")
    private String currentRoom;
    
    @Size(max = 255, message = "Target room must be at most 255 characters")
    private String targetRoom;
    
    private String description;
    
    private Boolean isFragile = false;
    
    private Boolean noStack = false;

    public BoxRequestDTO() {
    }

    public BoxRequestDTO(String currentRoom, String targetRoom, String description) {
        this.currentRoom = currentRoom;
        this.targetRoom = targetRoom;
        this.description = description;
        this.isFragile = false;
        this.noStack = false;
    }

    public String getCurrentRoom() {
        return currentRoom;
    }

    public void setCurrentRoom(String currentRoom) {
        this.currentRoom = currentRoom;
    }

    public String getTargetRoom() {
        return targetRoom;
    }

    public void setTargetRoom(String targetRoom) {
        this.targetRoom = targetRoom;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getIsFragile() {
        return isFragile;
    }

    public void setIsFragile(Boolean isFragile) {
        this.isFragile = isFragile;
    }

    public Boolean getNoStack() {
        return noStack;
    }

    public void setNoStack(Boolean noStack) {
        this.noStack = noStack;
    }
}
