package com.auracraft.controller;

import com.auracraft.entity.Product;
import com.auracraft.entity.ProductImage;
import com.auracraft.entity.ProductVariant;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ProductController – migrated from ProductServlet.
 * Handles all /api/products and /api/products/* endpoints.
 */
@RestController
@RequestMapping("/api/products")
@Transactional(readOnly = true)
public class ProductController {

    private static final Logger LOG = Logger.getLogger(ProductController.class.getName());
    private static final ObjectMapper mapper = new ObjectMapper();

    @PersistenceContext
    private EntityManager em;

    // ── GET /api/products  (list) ──────────────────────────────────────────
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getProducts(
            @RequestParam(name = "view", required = false) String view,
            @RequestParam(name = "sort", required = false) String sort,
            HttpServletRequest request) {
        try {
            if ("categories".equals(view)) return handleCategories();
            if ("bestsellers".equals(sort)) return handleBestSellers();
            if ("newest".equals(sort)) return handleNewest();
            return handleAllProducts();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "ProductController error", e);
            return ResponseEntity.internalServerError()
                    .body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── GET /api/products/{id} ───────────────────────────────────────────────
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getProductById(@PathVariable(name = "id") Integer id, HttpServletRequest request) {
        try {
            return getSingleProduct(id);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "ProductController error", e);
            return ResponseEntity.internalServerError()
                    .body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    private ResponseEntity<String> getSingleProduct(int productId) {
        Product p = em.find(Product.class, productId);
        if (p == null || Boolean.TRUE.equals(p.getIsDeleted())) {
            return ResponseEntity.status(404).body("{\"success\":false,\"message\":\"Product not found\"}");
        }

        List<ProductVariant> variants = em.createQuery(
                "SELECT v FROM ProductVariant v WHERE v.product.id = :pid ORDER BY v.id ASC", ProductVariant.class)
                .setParameter("pid", productId).getResultList();

        List<Object[]> invRows = em.createQuery(
                "SELECT i.productVariant.id, i.quantityOnHand FROM Inventory i WHERE i.productVariant.product.id = :pid",
                Object[].class).setParameter("pid", productId).getResultList();
        Map<Integer, Integer> invMap = new HashMap<>();
        for (Object[] row : invRows) invMap.put((Integer) row[0], ((Number) row[1]).intValue());

        List<ProductImage> images = em.createQuery(
                "SELECT i FROM ProductImage i WHERE i.product.id = :pid ORDER BY i.isPrimary DESC, i.id ASC",
                ProductImage.class).setParameter("pid", productId).getResultList();

        StringBuilder variantsArr = new StringBuilder("[");
        for (int i = 0; i < variants.size(); i++) {
            ProductVariant v = variants.get(i);
            int stock = invMap.getOrDefault(v.getId(), 0);
            if (i > 0) variantsArr.append(",");
            variantsArr.append("{")
                .append("\"id\":").append(v.getId()).append(",")
                .append("\"sku\":\"").append(esc(v.getSkuVariant())).append("\",")
                .append("\"color\":\"").append(esc(v.getMetalColor())).append("\",")
                .append("\"size\":\"").append(esc(v.getSizeLength())).append("\",")
                .append("\"price\":").append(v.getPrice() != null ? v.getPrice() : BigDecimal.ZERO).append(",")
                .append("\"stock\":").append(stock);
            if (v.getCompareAtPrice() != null) variantsArr.append(",\"compareAtPrice\":").append(v.getCompareAtPrice());
            variantsArr.append("}");
        }
        variantsArr.append("]");

        StringBuilder imagesArr = new StringBuilder("[");
        for (int i = 0; i < images.size(); i++) {
            ProductImage img = images.get(i);
            if (i > 0) imagesArr.append(",");
            imagesArr.append("{")
                .append("\"id\":").append(img.getId()).append(",")
                .append("\"url\":\"").append(esc(img.getImageUrl())).append("\",")
                .append("\"isPrimary\":").append(Boolean.TRUE.equals(img.getIsPrimary()))
                .append("}");
        }
        imagesArr.append("]");

        String json = "{\"success\":true,\"product\":{"
            + "\"id\":" + p.getId() + ","
            + "\"name\":\"" + esc(p.getName()) + "\","
            + "\"brand\":\"" + esc(p.getBrand()) + "\","
            + "\"summary\":\"" + esc(p.getSummary()) + "\","
            + "\"description\":\"" + esc(p.getDescription()) + "\","
            + "\"category\":\"" + esc(p.getCategory() != null ? p.getCategory().getName() : "") + "\","
            + "\"categoryId\":" + (p.getCategory() != null ? p.getCategory().getId() : 0) + ","
            + "\"gender\":\"" + esc(p.getGender()) + "\","
            + "\"isCustomisable\":" + Boolean.TRUE.equals(p.getIsCustomisable()) + ","
            + "\"warrantyPeriod\":\"" + esc(p.getWarrantyPeriod() != null ? p.getWarrantyPeriod() : "") + "\","
            + "\"availabilityStatus\":\"" + esc(p.getAvailabilityStatus()) + "\","
            + "\"requiresDeposit\":" + Boolean.TRUE.equals(p.getRequiresDeposit()) + ","
            + "\"createdAt\":\"" + (p.getCreatedAt() != null ? p.getCreatedAt().toString() : "") + "\","
            + "\"variants\":" + variantsArr + ","
            + "\"images\":" + imagesArr
            + "}}";
        return ResponseEntity.ok(json);
    }

    // ── All products list ────────────────────────────────────────────────────
    private ResponseEntity<String> handleAllProducts() {
        List<ProductVariant> allVariants = em.createQuery(
                "SELECT v FROM ProductVariant v JOIN FETCH v.product p LEFT JOIN FETCH p.category " +
                "WHERE (p.isDeleted = false OR p.isDeleted IS NULL) " +
                "AND (v.isDeleted = false OR v.isDeleted IS NULL) ORDER BY p.id ASC, v.id ASC",
                ProductVariant.class).getResultList();

        Map<Integer, String> primaryImages = buildPrimaryImageMap();
        Map<Integer, Integer> inventoryMap = buildInventoryMap();

        LinkedHashMap<Integer, List<ProductVariant>> variantsByProduct = new LinkedHashMap<>();
        LinkedHashMap<Integer, Product> productById = new LinkedHashMap<>();
        for (ProductVariant v : allVariants) {
            if (v.getProduct() == null) continue;
            variantsByProduct.computeIfAbsent(v.getProduct().getId(), k -> new ArrayList<>()).add(v);
            productById.putIfAbsent(v.getProduct().getId(), v.getProduct());
        }

        StringBuilder listArr = new StringBuilder("[");
        boolean first = true;
        for (Map.Entry<Integer, List<ProductVariant>> entry : variantsByProduct.entrySet()) {
            if (!first) listArr.append(",");
            first = false;
            Product p = productById.get(entry.getKey());
            if (p == null) continue;
            listArr.append(buildListObj(p, entry.getValue(), primaryImages.getOrDefault(entry.getKey(), ""), inventoryMap));
        }
        listArr.append("]");
        return ResponseEntity.ok("{\"success\":true,\"products\":" + listArr + "}");
    }

    // ── Bestsellers (Web + POS Combined) ─────────────────────────────────────
    private ResponseEntity<String> handleBestSellers() {
        List<Object[]> webTop = em.createQuery(
                "SELECT oi.productVariant.product.id, SUM(oi.quantity) FROM OrderItem oi " +
                "WHERE (oi.productVariant.product.isDeleted = false OR oi.productVariant.product.isDeleted IS NULL) " +
                "GROUP BY oi.productVariant.product.id", Object[].class).getResultList();

        List<Object[]> posTop = em.createQuery(
                "SELECT poi.productVariant.product.id, SUM(poi.quantity) FROM PosOrderItem poi " +
                "WHERE (poi.productVariant.product.isDeleted = false OR poi.productVariant.product.isDeleted IS NULL) " +
                "GROUP BY poi.productVariant.product.id", Object[].class).getResultList();

        Map<Integer, Long> totalsMap = new HashMap<>();
        for (Object[] r : webTop) {
            if (r[0] != null) {
                Integer pId = (Integer) r[0];
                Long qty = r[1] != null ? ((Number) r[1]).longValue() : 0L;
                totalsMap.put(pId, totalsMap.getOrDefault(pId, 0L) + qty);
            }
        }
        for (Object[] r : posTop) {
            if (r[0] != null) {
                Integer pId = (Integer) r[0];
                Long qty = r[1] != null ? ((Number) r[1]).longValue() : 0L;
                totalsMap.put(pId, totalsMap.getOrDefault(pId, 0L) + qty);
            }
        }

        List<Map.Entry<Integer, Long>> sortedEntries = new ArrayList<>(totalsMap.entrySet());
        sortedEntries.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < Math.min(8, sortedEntries.size()); i++) {
            ids.add(sortedEntries.get(i).getKey());
        }

        if (ids.isEmpty()) {
            List<Product> newestProds = em.createQuery(
                    "SELECT p FROM Product p WHERE (p.isDeleted = false OR p.isDeleted IS NULL) ORDER BY p.createdAt DESC", Product.class)
                    .setMaxResults(8).getResultList();
            for (Product p : newestProds) ids.add(p.getId());
        }

        if (ids.isEmpty()) return ResponseEntity.ok("{\"success\":true,\"products\":[]}");

        List<ProductVariant> variants = em.createQuery(
                "SELECT v FROM ProductVariant v JOIN FETCH v.product p LEFT JOIN FETCH p.category WHERE p.id IN :ids " +
                "AND (v.isDeleted = false OR v.isDeleted IS NULL) ORDER BY p.id ASC, v.id ASC", ProductVariant.class)
                .setParameter("ids", ids).getResultList();

        Map<Integer, String> primaryImages = buildPrimaryImageMap();
        Map<Integer, Integer> inventoryMap = buildInventoryMap();

        String arr = buildProductArray(ids, variants, primaryImages, inventoryMap);
        return ResponseEntity.ok("{\"success\":true,\"products\":" + arr + "}");
    }

    // ── Newest ───────────────────────────────────────────────────────────────
    private ResponseEntity<String> handleNewest() {
        Map<Integer, String> primaryImages = buildPrimaryImageMap();
        Map<Integer, Integer> inventoryMap = buildInventoryMap();
        String arr = newestProductObjs(8, primaryImages, inventoryMap);
        return ResponseEntity.ok("{\"success\":true,\"products\":" + arr + "}");
    }

    // ── Categories ───────────────────────────────────────────────────────────
    private ResponseEntity<String> handleCategories() {
        List<Object[]> rows = em.createQuery(
                "SELECT c.id, c.name, COUNT(p.id) FROM Category c " +
                "LEFT JOIN Product p ON p.category = c AND (p.isDeleted = false OR p.isDeleted IS NULL) " +
                "GROUP BY c.id, c.name ORDER BY c.name ASC", Object[].class).getResultList();

        StringBuilder arr = new StringBuilder("[");
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) arr.append(",");
            Object[] r = rows.get(i);
            Integer catId = (Integer) r[0];
            String name = (String) r[1];
            Long count = (Long) r[2];

            // Find most sold product image in this category
            String imageUrl = null;
            try {
                String bestSellerQuery = "SELECT pi.imageUrl FROM OrderItem oi " +
                                         "JOIN oi.productVariant v " +
                                         "JOIN v.product p " +
                                         "JOIN ProductImage pi ON pi.product = p AND pi.isPrimary = true " +
                                         "WHERE p.category.id = :catId AND (p.isDeleted = false OR p.isDeleted IS NULL) " +
                                         "GROUP BY pi.imageUrl " +
                                         "ORDER BY SUM(oi.quantity) DESC";
                List<String> bestImages = em.createQuery(bestSellerQuery, String.class)
                                            .setParameter("catId", catId)
                                            .setMaxResults(1)
                                            .getResultList();
                if (!bestImages.isEmpty()) {
                    imageUrl = bestImages.get(0);
                } else {
                    // Fallback to ANY image in this category, prioritizing primary images
                    String fallbackQuery = "SELECT pi.imageUrl FROM ProductImage pi " +
                                           "JOIN pi.product p " +
                                           "WHERE p.category.id = :catId AND (p.isDeleted = false OR p.isDeleted IS NULL) " +
                                           "ORDER BY pi.isPrimary DESC, pi.id ASC";
                    List<String> fallbacks = em.createQuery(fallbackQuery, String.class)
                                               .setParameter("catId", catId)
                                               .setMaxResults(1)
                                               .getResultList();
                    if (!fallbacks.isEmpty()) {
                        imageUrl = fallbacks.get(0);
                    }
                }
            } catch (Exception e) {
                // Ignore query errors, just fallback to null (which uses default image on frontend)
            }

            arr.append("{")
                .append("\"id\":").append(catId).append(",")
                .append("\"name\":\"").append(esc(name)).append("\",")
                .append("\"productCount\":").append(count);
            
            if (imageUrl != null) {
                arr.append(",\"image\":\"").append(esc(imageUrl)).append("\"");
            }
            
            arr.append("}");
        }
        arr.append("]");
        return ResponseEntity.ok("{\"success\":true,\"categories\":" + arr + "}");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String newestProductObjs(int limit, Map<Integer, String> primaryImages, Map<Integer, Integer> inventoryMap) {
        List<Product> topProds = em.createQuery(
                "SELECT p FROM Product p WHERE p.isDeleted = false OR p.isDeleted IS NULL " +
                "ORDER BY p.createdAt DESC", Product.class).setMaxResults(limit).getResultList();
        if (topProds.isEmpty()) return "[]";

        List<Integer> ids = new ArrayList<>();
        for (Product p : topProds) ids.add(p.getId());

        List<ProductVariant> variants = em.createQuery(
                "SELECT v FROM ProductVariant v JOIN FETCH v.product p LEFT JOIN FETCH p.category WHERE p.id IN :ids " +
                "AND (v.isDeleted = false OR v.isDeleted IS NULL)", ProductVariant.class)
                .setParameter("ids", ids).getResultList();

        return buildProductArray(ids, variants, primaryImages, inventoryMap);
    }

    private String buildProductArray(List<Integer> sortedIds, List<ProductVariant> variants,
                                     Map<Integer, String> primaryImages, Map<Integer, Integer> inventoryMap) {
        LinkedHashMap<Integer, List<ProductVariant>> variantsByProduct = new LinkedHashMap<>();
        LinkedHashMap<Integer, Product> productById = new LinkedHashMap<>();
        for (ProductVariant v : variants) {
            if (v.getProduct() == null) continue;
            variantsByProduct.computeIfAbsent(v.getProduct().getId(), k -> new ArrayList<>()).add(v);
            productById.putIfAbsent(v.getProduct().getId(), v.getProduct());
        }

        StringBuilder arr = new StringBuilder("[");
        boolean first = true;
        for (Integer pid : sortedIds) {
            List<ProductVariant> vlist = variantsByProduct.get(pid);
            if (vlist == null || vlist.isEmpty()) continue;
            Product p = productById.get(pid);
            if (!first) arr.append(",");
            first = false;
            arr.append(buildListObj(p, vlist, primaryImages.getOrDefault(pid, ""), inventoryMap));
        }
        arr.append("]");
        return arr.toString();
    }

    private String buildListObj(Product p, List<ProductVariant> vlist, String primaryImageUrl, Map<Integer, Integer> inventoryMap) {
        BigDecimal minPrice = null;
        BigDecimal minCompare = null;
        int totalStock = 0;
        
        java.util.Set<String> colors = new java.util.HashSet<>();
        java.util.Set<String> sizes = new java.util.HashSet<>();
        
        for (ProductVariant v : vlist) {
            if (v.getPrice() != null) {
                if (minPrice == null || v.getPrice().compareTo(minPrice) < 0) minPrice = v.getPrice();
            }
            if (v.getCompareAtPrice() != null) {
                if (minCompare == null || v.getCompareAtPrice().compareTo(minCompare) < 0) minCompare = v.getCompareAtPrice();
            }
            totalStock += inventoryMap.getOrDefault(v.getId(), 0);
            
            if (v.getMetalColor() != null && !v.getMetalColor().trim().isEmpty()) {
                colors.add(esc(v.getMetalColor()));
            }
            if (v.getSizeLength() != null && !v.getSizeLength().trim().isEmpty()) {
                sizes.add(esc(v.getSizeLength()));
            }
        }

        boolean inStock = totalStock > 0;

        StringBuilder cArr = new StringBuilder("[");
        boolean cFirst = true;
        for (String c : colors) {
            if (!cFirst) cArr.append(",");
            cFirst = false;
            cArr.append("\"").append(c).append("\"");
        }
        cArr.append("]");

        StringBuilder sArr = new StringBuilder("[");
        boolean sFirst = true;
        for (String s : sizes) {
            if (!sFirst) sArr.append(",");
            sFirst = false;
            sArr.append("\"").append(s).append("\"");
        }
        sArr.append("]");

        return "{"
            + "\"id\":" + p.getId() + ","
            + "\"name\":\"" + esc(p.getName()) + "\","
            + "\"displayName\":\"" + esc(p.getName()) + "\","
            + "\"category\":\"" + esc(p.getCategory() != null ? p.getCategory().getName() : "") + "\","
            + "\"price\":" + (minPrice != null ? minPrice : 0) + ","
            + "\"compareAtPrice\":" + (minCompare != null ? minCompare : "null") + ","
            + "\"primaryImage\":\"" + esc(primaryImageUrl) + "\","
            + "\"img\":\"" + esc(primaryImageUrl) + "\","
            + "\"stock\":" + totalStock + ","
            + "\"inStock\":" + inStock + ","
            + "\"gender\":\"" + esc(p.getGender() != null ? p.getGender() : "") + "\","
            + "\"brand\":\"" + esc(p.getBrand() != null ? p.getBrand() : "") + "\","
            + "\"colors\":" + cArr.toString() + ","
            + "\"sizes\":" + sArr.toString() + ","
            + "\"variants\": []"
            + "}";
    }

    private Map<Integer, String> buildPrimaryImageMap() {
        List<ProductImage> allImages = em.createQuery(
                "SELECT i FROM ProductImage i ORDER BY i.isPrimary DESC, i.id ASC", ProductImage.class).getResultList();
        Map<Integer, String> map = new HashMap<>();
        for (ProductImage img : allImages) {
            if (img.getProduct() != null && !map.containsKey(img.getProduct().getId())) {
                map.put(img.getProduct().getId(), img.getImageUrl());
            }
        }
        return map;
    }

    private Map<Integer, Integer> buildInventoryMap() {
        List<Object[]> invRows = em.createQuery(
                "SELECT i.productVariant.id, SUM(i.quantityOnHand) FROM Inventory i GROUP BY i.productVariant.id",
                Object[].class).getResultList();
        Map<Integer, Integer> map = new HashMap<>();
        for (Object[] r : invRows) {
            map.put((Integer) r[0], ((Number) r[1]).intValue());
        }
        return map;
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
}
