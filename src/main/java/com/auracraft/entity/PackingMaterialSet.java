package com.auracraft.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * PackingMaterialSet – a named set of materials used for packing a specific product type.
 * e.g. "Pendant Set" = Box x1 + Pendant Hook x1 + Thank You Card x1 + Courier Bag x1
 */
@Entity
@Table(name = "packing_material_sets")
public class PackingMaterialSet implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "set_name", nullable = false, length = 100)
    private String setName;

    @Column(length = 255)
    private String description;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "materialSet", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PackingSetItem> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }

    // ── Getters & Setters ─────────────────────────────────────────
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getSetName() { return setName; }
    public void setSetName(String setName) { this.setName = setName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public List<PackingSetItem> getItems() { return items; }
    public void setItems(List<PackingSetItem> items) { this.items = items; }
}
