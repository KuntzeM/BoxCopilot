package com.boxcopilot.backend.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "boxes")
public class Box {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String uuid;

    @Column
    private String currentRoom;

    @Column
    private String targetRoom;

    @Lob
    @Column
    private String description;

    @Column(name = "is_fragile", nullable = false)
    private Boolean isFragile = false;

    @Column(name = "no_stack", nullable = false)
    private Boolean noStack = false;

    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "box", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Item> items = new ArrayList<>();

    public Box() {
    }

    public Box(String uuid, String currentRoom, String targetRoom, String description) {
        this.uuid = uuid;
        this.currentRoom = currentRoom;
        this.targetRoom = targetRoom;
        this.description = description;
        this.isFragile = false;
        this.noStack = false;
    }

    public Long getId() { return id; }
    
    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    
    public String getCurrentRoom() { return currentRoom; }
    public void setCurrentRoom(String currentRoom) { this.currentRoom = currentRoom; }
    
    public String getTargetRoom() { return targetRoom; }
    public void setTargetRoom(String targetRoom) { this.targetRoom = targetRoom; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public Boolean getIsFragile() { return isFragile; }
    public void setIsFragile(Boolean isFragile) { this.isFragile = isFragile; }
    
    public Boolean getNoStack() { return noStack; }
    public void setNoStack(Boolean noStack) { this.noStack = noStack; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }
}
