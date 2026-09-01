package com.auracraft.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * PosOrderItem – items within a POS order.
 */
@Entity
@Table(name = "pos_order_items")
public class PosOrderItem implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pos_order_id", nullable = false)
    private PosOrder posOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant productVariant;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "cost_at_purchase", precision = 10, scale = 2)
    private BigDecimal costAtPurchase = BigDecimal.ZERO;

    @Column(name = "engraving_text", length = 200)
    private String engravingText;

    @Column(name = "custom_resize", length = 50)
    private String customResize;

    // ── Getters & Setters ─────────────────────────────────────────
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public PosOrder getPosOrder() { return posOrder; }
    public void setPosOrder(PosOrder posOrder) { this.posOrder = posOrder; }

    public ProductVariant getProductVariant() { return productVariant; }
    public void setProductVariant(ProductVariant productVariant) { this.productVariant = productVariant; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public BigDecimal getCostAtPurchase() { return costAtPurchase; }
    public void setCostAtPurchase(BigDecimal costAtPurchase) { this.costAtPurchase = costAtPurchase; }

    public String getEngravingText() { return engravingText; }
    public void setEngravingText(String engravingText) { this.engravingText = engravingText; }

    public String getCustomResize() { return customResize; }
    public void setCustomResize(String customResize) { this.customResize = customResize; }
}
