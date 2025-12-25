package com.boxcopilot.backend.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "items")
public class Item {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "box_id")
    private Box box;

    private String name;
    
    @Column(name = "image_path", length = 512)
    private String imagePath;

    public Item() {}

    public Item(Box box, String name) {
        this.box = box;
        this.name = name;
    }

    public Long getId() { return id; }
    public Box getBox() { return box; }
    public void setBox(Box box) { this.box = box; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }
}
