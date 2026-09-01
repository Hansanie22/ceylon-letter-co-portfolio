package com.auracraft.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * OrderItem entity – maps to the `order_items` table.
 */
@Entity
@Table(name = "order_items")
public class OrderItem implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant productVariant;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "price_at_purchase", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceAtPurchase;

    @Column(name = "cost_at_purchase", precision = 10, scale = 2)
    private BigDecimal costAtPurchase = BigDecimal.ZERO;

    @Column(name = "engraving_text", length = 100)
    private String engravingText;

    @Column(name = "custom_resize", length = 50)
    private String customResize;

    // ── Getters & Setters ─────────────────────────────────────────
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }

    public ProductVariant getProductVariant() { return productVariant; }
    public void setProductVariant(ProductVariant productVariant) { this.productVariant = productVariant; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public BigDecimal getPriceAtPurchase() { return priceAtPurchase; }
    public void setPriceAtPurchase(BigDecimal priceAtPurchase) { this.priceAtPurchase = priceAtPurchase; }

    public BigDecimal getCostAtPurchase() { return costAtPurchase; }
    public void setCostAtPurchase(BigDecimal costAtPurchase) { this.costAtPurchase = costAtPurchase; }

    public String getEngravingText() { return engravingText; }
    public void setEngravingText(String engravingText) { this.engravingText = engravingText; }

    public String getCustomResize() { return customResize; }
    public void setCustomResize(String customResize) { this.customResize = customResize; }
}
