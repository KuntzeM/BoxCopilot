package com.boxcopilot.backend.dto;

import java.util.List;

public class BoxPreviewDTO {
    private Long id;
    private String uuid;
    private String currentRoom;
    private String targetRoom;
    private String description;
    private List<ItemDTO> items;
    private Boolean isFragile;
    private Boolean noStack;

    public BoxPreviewDTO() {}

    public BoxPreviewDTO(Long id, String uuid, String currentRoom, String targetRoom, String description, List<ItemDTO> items) {
        this.id = id;
        this.uuid = uuid;
        this.currentRoom = currentRoom;
        this.targetRoom = targetRoom;
        this.description = description;
        this.items = items;
        this.isFragile = false;
        this.noStack = false;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public String getCurrentRoom() { return currentRoom; }
    public void setCurrentRoom(String currentRoom) { this.currentRoom = currentRoom; }
    public String getTargetRoom() { return targetRoom; }
    public void setTargetRoom(String targetRoom) { this.targetRoom = targetRoom; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<ItemDTO> getItems() { return items; }
    public void setItems(List<ItemDTO> items) { this.items = items; }
    public Boolean getIsFragile() { return isFragile; }
    public void setIsFragile(Boolean isFragile) { this.isFragile = isFragile; }
    public Boolean getNoStack() { return noStack; }
    public void setNoStack(Boolean noStack) { this.noStack = noStack; }

    public static class ItemDTO {
        private Long id;
        private String name;
        private String imageUrl;

        public ItemDTO() {}

        public ItemDTO(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    }
}
