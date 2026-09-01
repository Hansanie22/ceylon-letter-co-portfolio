package com.auracraft.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * CartItem entity – maps to the `cart_items` table.
 */
@Entity
@Table(name = "cart_items")
public class CartItem implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant productVariant;

    @Column(nullable = false)
    private Integer quantity = 1;

    @Column(name = "engraving_text", length = 100)
    private String engravingText;

    @Column(name = "custom_resize", length = 50)
    private String customResize;

    @Column(name = "added_at", insertable = false, updatable = false)
    private LocalDateTime addedAt;

    // ── Getters & Setters ─────────────────────────────────────────
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public ProductVariant getProductVariant() { return productVariant; }
    public void setProductVariant(ProductVariant productVariant) { this.productVariant = productVariant; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getEngravingText() { return engravingText; }
    public void setEngravingText(String engravingText) { this.engravingText = engravingText; }

    public String getCustomResize() { return customResize; }
    public void setCustomResize(String customResize) { this.customResize = customResize; }

    public LocalDateTime getAddedAt() { return addedAt; }
}
