package com.ceylonletterco.controller;

import com.ceylonletterco.entity.Product;
import com.ceylonletterco.entity.ProductImage;
import com.ceylonletterco.entity.StoreVideo;
import com.ceylonletterco.entity.User;
import com.ceylonletterco.service.CloudinaryService;
import com.ceylonletterco.service.StoreVideoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/api/admin/videos")
public class AdminVideoController {

    @Autowired
    private StoreVideoService storeVideoService;

    @Autowired
    private CloudinaryService cloudinaryService;

    @Autowired
    private com.ceylonletterco.service.AuditLogService auditLogService;

    private boolean isAuthorized(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return false;
        User u = (User) session.getAttribute("loggedInUser");
        if (u == null || u.getRole() == null) return false;
        String r = u.getRole().toUpperCase();
        return r.contains("ADMIN") || r.contains("MANAGER");
    }

    @GetMapping
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<?> getAllVideos(HttpServletRequest request) {
        if (!isAuthorized(request)) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "Unauthorized"));
        }

        List<StoreVideo> videos = storeVideoService.getAllVideos();
        List<Map<String, Object>> result = new ArrayList<>();
        int activeCount = 0;
        int taggedProductsCount = 0;

        for (StoreVideo v : videos) {
            if (v.isActive()) activeCount++;
            if (v.getProduct() != null) taggedProductsCount++;

            Map<String, Object> map = new HashMap<>();
            map.put("id", v.getId());
            map.put("title", v.getTitle());
            map.put("caption", v.getCaption());
            map.put("videoCategory", v.getVideoCategory());
            map.put("platform", v.getPlatform());
            map.put("videoUrl", v.getVideoUrl());
            map.put("thumbnailUrl", v.getThumbnailUrl());
            map.put("customerName", v.getCustomerName());
            map.put("rating", v.getRating());
            map.put("ctaText", v.getCtaText());
            map.put("displayOrder", v.getDisplayOrder());
            map.put("isActive", v.isActive());
            map.put("createdAt", v.getCreatedAt() != null ? v.getCreatedAt().toString() : "");
            map.put("productId", v.getProduct() != null ? v.getProduct().getId() : null);
            
            if (v.getTaggedVariants() != null && !v.getTaggedVariants().isEmpty()) {
                List<Integer> variantIds = new ArrayList<>();
                for (com.ceylonletterco.entity.ProductVariant pv : v.getTaggedVariants()) {
                    variantIds.add(pv.getId());
                }
                map.put("taggedVariantIds", variantIds);
            } else {
                map.put("taggedVariantIds", new ArrayList<>());
            }
            if (v.getProduct() != null) {
                Product p = v.getProduct();
                Map<String, Object> prodMap = new HashMap<>();
                prodMap.put("id", p.getId());
                prodMap.put("name", p.getName());
                prodMap.put("basePrice", p.getBasePrice());
                prodMap.put("sku", p.getSku());

                String primaryImg = "";
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
                prodMap.put("primaryImage", primaryImg);
                map.put("product", prodMap);
            } else {
                map.put("product", null);
            }

            result.add(map);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("videos", result);
        response.put("total", result.size());
        response.put("activeCount", activeCount);
        response.put("taggedCount", taggedProductsCount);

        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<?> createVideo(
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {

        if (!isAuthorized(request)) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "Unauthorized"));
        }

        try {
            String title = (String) payload.get("title");
            String caption = (String) payload.get("caption");
            String category = (String) payload.get("category");
            String platform = (String) payload.get("platform");
            String videoUrl = (String) payload.get("videoUrl");
            String thumbnailUrl = (String) payload.get("thumbnailUrl");
            String customerName = (String) payload.get("customerName");
            String ctaText = (String) payload.get("ctaText");

            Integer productId = null;
            if (payload.get("productId") != null) {
                productId = Integer.valueOf(payload.get("productId").toString());
            }

            List<Integer> taggedVariantIds = new ArrayList<>();
            if (payload.get("taggedVariantIds") instanceof List) {
                for (Object o : (List<?>) payload.get("taggedVariantIds")) {
                    if (o != null) taggedVariantIds.add(Integer.valueOf(o.toString()));
                }
            }

            Integer rating = null;
            if (payload.get("rating") != null) {
                rating = Integer.valueOf(payload.get("rating").toString());
            }

            Integer displayOrder = null;
            if (payload.get("displayOrder") != null) {
                displayOrder = Integer.valueOf(payload.get("displayOrder").toString());
            }

            if (videoUrl == null || videoUrl.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Please provide a video URL."));
            }

            StoreVideo saved = storeVideoService.createVideo(
                    title, caption, category, platform, videoUrl, thumbnailUrl,
                    productId, taggedVariantIds, customerName, rating, ctaText, displayOrder
            );

            auditLogService.log(request, "CREATE_STORE_VIDEO", "SETTINGS",
                    "Added store video reel: " + (title != null && !title.isBlank() ? title : "Reel #" + saved.getId()),
                    "SUCCESS");

            return ResponseEntity.ok(Map.of("success", true, "id", saved.getId(), "message", "Video saved successfully."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Error saving video: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateVideo(
            @PathVariable("id") Long id,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {

        if (!isAuthorized(request)) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "Unauthorized"));
        }

        try {
            String title = (String) payload.get("title");
            String caption = (String) payload.get("caption");
            String category = (String) payload.get("category");
            String platform = (String) payload.get("platform");
            String videoUrl = (String) payload.get("videoUrl");
            String thumbnailUrl = (String) payload.get("thumbnailUrl");
            String customerName = (String) payload.get("customerName");
            String ctaText = (String) payload.get("ctaText");

            Integer productId = null;
            if (payload.get("productId") != null) {
                productId = Integer.valueOf(payload.get("productId").toString());
            }

            List<Integer> taggedVariantIds = new ArrayList<>();
            if (payload.get("taggedVariantIds") instanceof List) {
                for (Object o : (List<?>) payload.get("taggedVariantIds")) {
                    if (o != null) taggedVariantIds.add(Integer.valueOf(o.toString()));
                }
            }

            Integer rating = null;
            if (payload.get("rating") != null) {
                rating = Integer.valueOf(payload.get("rating").toString());
            }

            Integer displayOrder = null;
            if (payload.get("displayOrder") != null) {
                displayOrder = Integer.valueOf(payload.get("displayOrder").toString());
            }

            Boolean isActive = null;
            if (payload.get("isActive") != null) {
                isActive = Boolean.valueOf(payload.get("isActive").toString());
            }

            StoreVideo updated = storeVideoService.updateVideo(
                    id, title, caption, category, platform, videoUrl, thumbnailUrl,
                    productId, taggedVariantIds, customerName, rating, ctaText, displayOrder, isActive
            );

            auditLogService.log(request, "UPDATE_STORE_VIDEO", "SETTINGS",
                    "Updated store video reel #" + id,
                    "SUCCESS");

            return ResponseEntity.ok(Map.of("success", true, "id", updated.getId(), "message", "Video updated successfully."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<?> toggleVideoStatus(@PathVariable("id") Long id, HttpServletRequest request) {
        if (!isAuthorized(request)) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "Unauthorized"));
        }
        try {
            boolean active = storeVideoService.toggleActive(id);
            return ResponseEntity.ok(Map.of("success", true, "isActive", active));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteVideo(@PathVariable("id") Long id, HttpServletRequest request) {
        if (!isAuthorized(request)) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "Unauthorized"));
        }
        try {
            storeVideoService.deleteVideo(id);

            auditLogService.log(request, "DELETE_STORE_VIDEO", "SETTINGS",
                    "Deleted store video reel #" + id,
                    "SUCCESS");

            return ResponseEntity.ok(Map.of("success", true, "message", "Video deleted successfully."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadVideoFile(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        if (!isAuthorized(request)) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "Unauthorized"));
        }
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "File is empty."));
            }
            // Cloudinary upload handles both image & video automatically when resource_type is auto
            String url = cloudinaryService.uploadImage(file);
            return ResponseEntity.ok(Map.of("success", true, "url", url));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Upload failed: " + e.getMessage()));
        }
    }
}
