package com.ceylonletterco.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * StoreVideo entity – manages social media reels, customer review videos,
 * and brand craftsmanship videos across the store and admin dashboard.
 */
@Entity
@Table(name = "store_videos")
public class StoreVideo implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String caption;

    @Column(name = "video_category", nullable = false, length = 50)
    private String videoCategory = "SHOP_THE_LOOK"; // SHOP_THE_LOOK, CUSTOMER_REVIEW, CRAFTSMANSHIP

    @Column(nullable = false, length = 50)
    private String platform = "INSTAGRAM"; // INSTAGRAM, TIKTOK, YOUTUBE_SHORT, CLOUDINARY_MP4, DIRECT_URL

    @Column(name = "video_url", nullable = false, length = 1000)
    private String videoUrl;

    @Column(name = "thumbnail_url", length = 1000)
    private String thumbnailUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "store_video_variants",
        joinColumns = @JoinColumn(name = "video_id"),
        inverseJoinColumns = @JoinColumn(name = "variant_id")
    )
    private java.util.Set<ProductVariant> taggedVariants = new java.util.HashSet<>();

    @Column(name = "customer_name", length = 150)
    private String customerName;

    @Column(name = "rating")
    private Integer rating = 5;

    @Column(name = "cta_text", length = 100)
    private String ctaText = "Shop The Look";

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // ── Getters & Setters ─────────────────────────────────────────
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }

    public String getVideoCategory() { return videoCategory; }
    public void setVideoCategory(String videoCategory) { this.videoCategory = videoCategory; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }

    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public java.util.Set<ProductVariant> getTaggedVariants() { return taggedVariants; }
    public void setTaggedVariants(java.util.Set<ProductVariant> taggedVariants) { this.taggedVariants = taggedVariants; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }

    public String getCtaText() { return ctaText; }
    public void setCtaText(String ctaText) { this.ctaText = ctaText; }

    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
