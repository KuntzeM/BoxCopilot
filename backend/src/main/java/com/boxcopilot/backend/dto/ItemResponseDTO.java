package com.boxcopilot.backend.dto;

/**
 * DTO for Item responses.
 */
public class ItemResponseDTO {
    
    private Long id;
    private String name;
    private Long boxId;
    private String boxUuid;
    private String boxCurrentRoom;
    private String boxTargetRoom;

    public ItemResponseDTO() {
    }

    public ItemResponseDTO(Long id, String name, Long boxId, String boxUuid, String boxCurrentRoom, String boxTargetRoom) {
        this.id = id;
        this.name = name;
        this.boxId = boxId;
        this.boxUuid = boxUuid;
        this.boxCurrentRoom = boxCurrentRoom;
        this.boxTargetRoom = boxTargetRoom;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getBoxUuid() {
        return boxUuid;
    }

    public void setBoxUuid(String boxUuid) {
        this.boxUuid = boxUuid;
    }

    public String getBoxCurrentRoom() {
        return boxCurrentRoom;
    }

    public void setBoxCurrentRoom(String boxCurrentRoom) {
        this.boxCurrentRoom = boxCurrentRoom;
    }

    public String getBoxTargetRoom() {
        return boxTargetRoom;
    }

    public void setBoxTargetRoom(String boxTargetRoom) {
        this.boxTargetRoom = boxTargetRoom;
    }
}

