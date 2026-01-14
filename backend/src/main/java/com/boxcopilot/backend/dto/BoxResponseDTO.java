package com.boxcopilot.backend.dto;

import java.time.Instant;
import java.util.List;

/**
 * DTO for Box responses.
 * Exposes only necessary data to the client.
 */
public class BoxResponseDTO {
    
    private Long id;
    private String uuid;
    private Integer boxNumber;
    private String currentRoom;
    private String targetRoom;
    private String description;
    private Instant createdAt;
    private List<ItemResponseDTO> items;
    private Boolean isFragile;
    private Boolean noStack;
    private Boolean isMovedToTarget;
    private Boolean labelPrinted;

    public BoxResponseDTO() {
    }

    public BoxResponseDTO(Long id, String uuid, String currentRoom, String targetRoom, String description, Instant createdAt) {
        this.id = id;
        this.uuid = uuid;
        this.boxNumber = null;
        this.currentRoom = currentRoom;
        this.targetRoom = targetRoom;
        this.description = description;
        this.createdAt = createdAt;
        this.isFragile = false;
        this.noStack = false;
        this.isMovedToTarget = false;
        this.labelPrinted = false;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public Integer getBoxNumber() {
        return boxNumber;
    }

    public void setBoxNumber(Integer boxNumber) {
        this.boxNumber = boxNumber;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public List<ItemResponseDTO> getItems() {
        return items;
    }

    public void setItems(List<ItemResponseDTO> items) {
        this.items = items;
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

    public Boolean getIsMovedToTarget() {
        return isMovedToTarget;
    }

    public void setIsMovedToTarget(Boolean isMovedToTarget) {
        this.isMovedToTarget = isMovedToTarget;
    }

    public Boolean getLabelPrinted() {
        return labelPrinted;
    }

    public void setLabelPrinted(Boolean labelPrinted) {
        this.labelPrinted = labelPrinted;
    }
}
