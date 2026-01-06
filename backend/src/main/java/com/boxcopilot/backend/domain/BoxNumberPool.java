package com.boxcopilot.backend.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Entity representing the box number pool for reusable box numbering.
 */
@Entity
@Table(name = "box_number_pool")
public class BoxNumberPool {

    @Id
    @Column(name = "box_number", nullable = false)
    private Integer boxNumber;

    @Column(name = "is_available", nullable = false)
    private Boolean isAvailable = true;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public BoxNumberPool() {
    }

    public BoxNumberPool(Integer boxNumber, Boolean isAvailable, Instant lastUsedAt, Instant createdAt) {
        this.boxNumber = boxNumber;
        this.isAvailable = isAvailable;
        this.lastUsedAt = lastUsedAt;
        this.createdAt = createdAt;
    }

    public Integer getBoxNumber() {
        return boxNumber;
    }

    public void setBoxNumber(Integer boxNumber) {
        this.boxNumber = boxNumber;
    }

    public Boolean getIsAvailable() {
        return isAvailable;
    }

    public void setIsAvailable(Boolean isAvailable) {
        this.isAvailable = isAvailable;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
