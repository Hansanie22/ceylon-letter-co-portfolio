package com.auracraft.service;

import com.auracraft.entity.Product;
import com.auracraft.entity.StoreVideo;
import com.auracraft.repository.StoreVideoRepository;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class StoreVideoService {

    @Autowired
    private StoreVideoRepository storeVideoRepository;

    @Autowired
    private CloudinaryService cloudinaryService;

    @Autowired
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<StoreVideo> getAllVideos() {
        return storeVideoRepository.findAllByOrderByDisplayOrderAscCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<StoreVideo> getActiveVideos(String category) {
        if (category != null && !category.trim().isEmpty() && !"ALL".equalsIgnoreCase(category.trim())) {
            return storeVideoRepository.findByVideoCategoryAndIsActiveTrueOrderByDisplayOrderAscCreatedAtDesc(category.trim().toUpperCase());
        }
        return storeVideoRepository.findByIsActiveTrueOrderByDisplayOrderAscCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<StoreVideo> getVideosByProduct(Integer productId) {
        return storeVideoRepository.findByProductIdAndIsActiveTrueOrderByDisplayOrderAsc(productId);
    }

    @Transactional(readOnly = true)
    public Optional<StoreVideo> getVideoById(Long id) {
        return storeVideoRepository.findById(id);
    }

    public StoreVideo createVideo(String title, String caption, String category, String platform,
                                   String videoUrl, String thumbnailUrl, Integer productId, List<Integer> taggedVariantIds,
                                   String customerName, Integer rating, String ctaText, Integer displayOrder) {
        StoreVideo video = new StoreVideo();
        video.setTitle(title != null && !title.trim().isEmpty() ? title.trim() : "AuraCraft Studio Piece");
        video.setCaption(caption != null ? caption.trim() : "");
        video.setVideoCategory(category != null && !category.trim().isEmpty() ? category.trim().toUpperCase() : "SHOP_THE_LOOK");
        video.setPlatform(platform != null && !platform.trim().isEmpty() ? platform.trim().toUpperCase() : "INSTAGRAM");
        video.setVideoUrl(videoUrl != null ? videoUrl.trim() : "");
        video.setThumbnailUrl(thumbnailUrl != null ? thumbnailUrl.trim() : "");
        video.setCustomerName(customerName != null ? customerName.trim() : "");
        video.setRating(rating != null && rating >= 1 && rating <= 5 ? rating : 5);
        video.setCtaText(ctaText != null && !ctaText.trim().isEmpty() ? ctaText.trim() : "Shop The Look");

        if (productId != null && productId > 0) {
            Product p = entityManager.find(Product.class, productId);
            video.setProduct(p);
        } else {
            video.setProduct(null);
        }

        if (taggedVariantIds != null && !taggedVariantIds.isEmpty()) {
            java.util.Set<com.auracraft.entity.ProductVariant> variants = new java.util.HashSet<>();
            for (Integer vid : taggedVariantIds) {
                com.auracraft.entity.ProductVariant pv = entityManager.find(com.auracraft.entity.ProductVariant.class, vid);
                if (pv != null) variants.add(pv);
            }
            video.setTaggedVariants(variants);
        } else {
            video.setTaggedVariants(new java.util.HashSet<>());
        }

        if (displayOrder != null) {
            video.setDisplayOrder(displayOrder);
        } else {
            List<StoreVideo> all = storeVideoRepository.findAll();
            video.setDisplayOrder(all.size() + 1);
        }
        video.setActive(true);

        return storeVideoRepository.save(video);
    }

    public StoreVideo updateVideo(Long id, String title, String caption, String category, String platform,
                                   String videoUrl, String thumbnailUrl, Integer productId, List<Integer> taggedVariantIds,
                                   String customerName, Integer rating, String ctaText,
                                   Integer displayOrder, Boolean isActive) {
        StoreVideo video = storeVideoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Store video not found with ID: " + id));

        if (title != null && !title.trim().isEmpty()) video.setTitle(title.trim());
        if (caption != null) video.setCaption(caption.trim());
        if (category != null && !category.trim().isEmpty()) video.setVideoCategory(category.trim().toUpperCase());
        if (platform != null && !platform.trim().isEmpty()) video.setPlatform(platform.trim().toUpperCase());
        if (videoUrl != null && !videoUrl.trim().isEmpty()) video.setVideoUrl(videoUrl.trim());
        if (thumbnailUrl != null) video.setThumbnailUrl(thumbnailUrl.trim());
        if (customerName != null) video.setCustomerName(customerName.trim());
        if (rating != null && rating >= 1 && rating <= 5) video.setRating(rating);
        if (ctaText != null && !ctaText.trim().isEmpty()) video.setCtaText(ctaText.trim());
        if (displayOrder != null) video.setDisplayOrder(displayOrder);
        if (isActive != null) video.setActive(isActive);

        if (productId != null) {
            if (productId > 0) {
                Product p = entityManager.find(Product.class, productId);
                video.setProduct(p);
            } else {
                video.setProduct(null);
            }
        }

        if (taggedVariantIds != null) {
            java.util.Set<com.auracraft.entity.ProductVariant> variants = new java.util.HashSet<>();
            for (Integer vid : taggedVariantIds) {
                com.auracraft.entity.ProductVariant pv = entityManager.find(com.auracraft.entity.ProductVariant.class, vid);
                if (pv != null) variants.add(pv);
            }
            video.setTaggedVariants(variants);
        }

        return storeVideoRepository.save(video);
    }

    public void deleteVideo(Long id) {
        storeVideoRepository.findById(id).ifPresent(video -> {
            try {
                if (video.getVideoUrl() != null && video.getVideoUrl().contains("cloudinary")) {
                    String[] parts = video.getVideoUrl().split("/");
                    String publicIdWithExt = parts[parts.length - 1];
                    String publicId = publicIdWithExt.contains(".") ? publicIdWithExt.substring(0, publicIdWithExt.lastIndexOf('.')) : publicIdWithExt;
                    cloudinaryService.deleteFile(publicId, "video");
                }
            } catch (Exception ignored) {}
            storeVideoRepository.delete(video);
        });
    }

    public boolean toggleActive(Long id) {
        StoreVideo video = storeVideoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Store video not found with ID: " + id));
        video.setActive(!video.isActive());
        storeVideoRepository.save(video);
        return video.isActive();
    }
}
