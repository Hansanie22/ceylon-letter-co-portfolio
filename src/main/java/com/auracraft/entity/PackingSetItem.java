package com.auracraft.entity;

import jakarta.persistence.*;
import java.io.Serializable;

/**
 * PackingSetItem – line items inside a PackingMaterialSet.
 */
@Entity
@Table(name = "packing_set_items")
public class PackingSetItem implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "set_id", nullable = false)
    private PackingMaterialSet materialSet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id", nullable = false)
    private PackingMaterial material;

    @Column(name = "qty_used", nullable = false)
    private Integer qtyUsed = 1;

    // ── Getters & Setters ─────────────────────────────────────────
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public PackingMaterialSet getMaterialSet() { return materialSet; }
    public void setMaterialSet(PackingMaterialSet materialSet) { this.materialSet = materialSet; }

    public PackingMaterial getMaterial() { return material; }
    public void setMaterial(PackingMaterial material) { this.material = material; }

    public Integer getQtyUsed() { return qtyUsed; }
    public void setQtyUsed(Integer qtyUsed) { this.qtyUsed = qtyUsed; }
}
