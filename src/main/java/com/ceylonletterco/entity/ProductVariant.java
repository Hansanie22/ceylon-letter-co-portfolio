package com.ceylonletterco.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * ProductVariant entity – maps to the `product_variants` table.
 */
@Entity
@Table(name = "product_variants")
public class ProductVariant implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "sku_variant", nullable = false, unique = true, length = 50)
    private String skuVariant;

    @Column(name = "metal_color", length = 50)
    private String metalColor;

    @Column(name = "size_length", length = 50)
    private String sizeLength;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "compare_at_price", precision = 10, scale = 2)
    private BigDecimal compareAtPrice;

    @Column(name = "cost_price", precision = 10, scale = 2)
    private BigDecimal costPrice = BigDecimal.ZERO;

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    // ── Getters & Setters ─────────────────────────────────────────
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public String getSkuVariant() { return skuVariant; }
    public void setSkuVariant(String skuVariant) { this.skuVariant = skuVariant; }

    public String getMetalColor() { return metalColor; }
    public void setMetalColor(String metalColor) { this.metalColor = metalColor; }

    public String getSizeLength() { return sizeLength; }
    public void setSizeLength(String sizeLength) { this.sizeLength = sizeLength; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getCompareAtPrice() { return compareAtPrice; }
    public void setCompareAtPrice(BigDecimal compareAtPrice) { this.compareAtPrice = compareAtPrice; }

    public BigDecimal getCostPrice() { return costPrice; }
    public void setCostPrice(BigDecimal costPrice) { this.costPrice = costPrice; }

    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }
}
