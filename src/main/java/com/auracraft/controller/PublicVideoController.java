package com.auracraft.controller;

import com.auracraft.entity.Product;
import com.auracraft.entity.ProductImage;
import com.auracraft.entity.StoreVideo;
import com.auracraft.service.StoreVideoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/public/videos")
public class PublicVideoController {

    @Autowired
    private StoreVideoService storeVideoService;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<?> getPublicVideos(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "productId", required = false) Integer productId) {

        List<Map<String, Object>> result = new ArrayList<>();

        try {
            List<StoreVideo> videos;
            if (productId != null && productId > 0) {
                videos = storeVideoService.getVideosByProduct(productId);
            } else {
                videos = storeVideoService.getActiveVideos(category);
            }

            for (StoreVideo v : videos) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", v.getId());
                map.put("title", v.getTitle());
                map.put("caption", v.getCaption());
                map.put("videoCategory", v.getVideoCategory());
                map.put("platform", v.getPlatform());
                map.put("videoUrl", v.getVideoUrl());
                map.put("thumbnailUrl", v.getThumbnailUrl());
                map.put("customerName", v.getCustomerName());
                map.put("rating", v.getRating() != null ? v.getRating() : 5);
                map.put("ctaText", v.getCtaText() != null ? v.getCtaText() : "Shop The Look");
                map.put("displayOrder", v.getDisplayOrder());
                map.put("createdAt", v.getCreatedAt() != null ? v.getCreatedAt().toString() : "");

                try {
                    Product p = v.getProduct();
                    if (p != null) {
                        Map<String, Object> prodMap = new HashMap<>();
                        prodMap.put("id", p.getId());
                        prodMap.put("name", p.getName());
                        prodMap.put("basePrice", p.getBasePrice());
                        prodMap.put("isCustomisable", p.getIsCustomisable());
                        prodMap.put("sku", p.getSku());

                        if (v.getTaggedVariants() != null && !v.getTaggedVariants().isEmpty()) {
                            java.util.List<Map<String, Object>> varList = new java.util.ArrayList<>();
                            java.math.BigDecimal minPrice = null;
                            java.math.BigDecimal maxPrice = null;
                            for (com.auracraft.entity.ProductVariant pv : v.getTaggedVariants()) {
                                Map<String, Object> vMap = new HashMap<>();
                                vMap.put("id", pv.getId());
                                vMap.put("price", pv.getPrice());
                                vMap.put("sku", pv.getSkuVariant());
                                vMap.put("color", pv.getMetalColor());
                                vMap.put("size", pv.getSizeLength());
                                varList.add(vMap);
                                
                                if (pv.getPrice() != null) {
                                    if (minPrice == null || pv.getPrice().compareTo(minPrice) < 0) minPrice = pv.getPrice();
                                    if (maxPrice == null || pv.getPrice().compareTo(maxPrice) > 0) maxPrice = pv.getPrice();
                                }
                            }
                            prodMap.put("taggedVariants", varList);
                            if (minPrice != null && maxPrice != null) {
                                if (minPrice.compareTo(maxPrice) == 0) {
                                    prodMap.put("priceDisplay", "Rs. " + minPrice);
                                    prodMap.put("isSpecificVariant", true); // Single price, can add to cart directly if 1 variant
                                } else {
                                    prodMap.put("priceDisplay", "Rs. " + minPrice + " - Rs. " + maxPrice);
                                    prodMap.put("isSpecificVariant", false); // Multiple prices, need to select
                                }
                            }
                        } else {
                            prodMap.put("taggedVariants", new java.util.ArrayList<>());
                            prodMap.put("isSpecificVariant", false);
                            prodMap.put("priceDisplay", "Rs. " + (p.getBasePrice() != null ? p.getBasePrice() : 0));
                        }

                        String primaryImg = "";
                        try {
                            if (p.getImages() != null && !p.getImages().isEmpty()) {
                                for (ProductImage img : p.getImages()) {
                                    if (Boolean.TRUE.equals(img.getIsPrimary())) {
                                        primaryImg = img.getImageUrl();
                                        break;
                                    }
                                }
                                if (primaryImg.isEmpty() && !p.getImages().isEmpty()) {
                                    primaryImg = p.getImages().get(0).getImageUrl();
                                }
                            }
                        } catch (Exception ignored) {}

                        if (primaryImg.isEmpty() && v.getThumbnailUrl() != null && !v.getThumbnailUrl().isEmpty()) {
                            primaryImg = v.getThumbnailUrl();
                        }
                        prodMap.put("primaryImage", primaryImg);
                        map.put("product", prodMap);
                    } else {
                        map.put("product", null);
                    }
                } catch (Exception ignored) {
                    map.put("product", null);
                }

                result.add(map);
            }
        } catch (Exception ex) {
            System.err.println("⚠️ PublicVideoController fallback triggered: " + ex.getMessage());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("videos", result);
        response.put("total", result.size());

        return ResponseEntity.ok(response);
    }
}
