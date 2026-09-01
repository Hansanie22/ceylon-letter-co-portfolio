package com.auracraft.controller;

import com.auracraft.entity.*;
import com.auracraft.entity.InventoryLog;
import com.auracraft.entity.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * AdminInventoryController – migrated from AdminInventoryServlet.
 * Handles /api/admin/inventory/* and /api/admin/variants/* endpoints.
 */
@RestController
@RequestMapping("/api/admin")
@Transactional
public class AdminInventoryController {

    @PersistenceContext
    private EntityManager em;
    
    @org.springframework.beans.factory.annotation.Autowired
    private com.auracraft.service.EmailVerificationService emailService;
    
    @org.springframework.beans.factory.annotation.Autowired
    private com.auracraft.service.NotificationService notificationService;

    @org.springframework.beans.factory.annotation.Autowired
    private com.auracraft.service.AuditLogService auditLogService;

    private boolean isAdminOrStaff(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        User u = (session != null) ? (User) session.getAttribute("loggedInUser") : null;
        if (u == null) return false;
        String role = u.getRole() != null ? u.getRole().toUpperCase() : "";
        return !"CUSTOMER".equals(role);
    }

    // ── GET /api/admin/inventory ─────────────────────────────────────────────
    @GetMapping(value = "/inventory", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<String> getInventory(HttpServletRequest request) {
        if (!isAdminOrStaff(request)) return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");

        List<Inventory> inventoryList = em.createQuery(
                "SELECT i FROM Inventory i JOIN FETCH i.productVariant v JOIN FETCH v.product p WHERE p.isDeleted = false AND (v.isDeleted = false OR v.isDeleted IS NULL) ORDER BY p.id ASC",
                Inventory.class).getResultList();

        StringBuilder arr = new StringBuilder("[");
        for (int i = 0; i < inventoryList.size(); i++) {
            if (i > 0) arr.append(",");
            Inventory inv = inventoryList.get(i);
            ProductVariant v = inv.getProductVariant();
            arr.append("{")
                .append("\"id\":").append(inv.getVariantId()).append(",")
                .append("\"variantId\":").append(v.getId()).append(",")
                .append("\"productId\":").append(v.getProduct().getId()).append(",")
                .append("\"productName\":\"").append(esc(v.getProduct().getName())).append("\",")
                .append("\"sku\":\"").append(esc(v.getSkuVariant())).append("\",")
                .append("\"quantity\":").append(inv.getQuantityOnHand()).append(",")
                .append("\"lastUpdated\":\"").append(inv.getUpdatedAt() != null ? inv.getUpdatedAt().toString() : "").append("\"")
                .append("}");
        }
        arr.append("]");
        return ResponseEntity.ok("{\"success\":true,\"inventory\":" + arr + "}");
    }

    // ── GET /api/admin/inventory/alerts — Low & Out-of-stock Product & Material Alerts ──
    @GetMapping(value = "/inventory/alerts", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<String> getStockAlerts(HttpServletRequest request) {
        if (!isAdminOrStaff(request)) return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");

        try {
            // 1. Fetch Product Variant Inventory Alerts
            List<Inventory> invList = em.createQuery(
                    "SELECT i FROM Inventory i JOIN FETCH i.productVariant v JOIN FETCH v.product p " +
                    "WHERE p.isDeleted = false AND (v.isDeleted = false OR v.isDeleted IS NULL) " +
                    "AND i.quantityOnHand <= COALESCE(i.lowStockThreshold, 5) " +
                    "ORDER BY i.quantityOnHand ASC, p.name ASC", Inventory.class).getResultList();

            // 2. Fetch Packing Materials Alerts
            List<PackingMaterial> matList = em.createQuery(
                    "SELECT m FROM PackingMaterial m " +
                    "WHERE m.qtyInStock <= COALESCE(m.lowStockThreshold, 10) " +
                    "ORDER BY m.qtyInStock ASC, m.name ASC", PackingMaterial.class).getResultList();

            int outOfStockCount = 0;
            int lowStockCount = 0;

            StringBuilder invSb = new StringBuilder("[");
            for (int i = 0; i < invList.size(); i++) {
                if (i > 0) invSb.append(",");
                Inventory inv = invList.get(i);
                ProductVariant v = inv.getProductVariant();
                int qty = inv.getQuantityOnHand() != null ? inv.getQuantityOnHand() : 0;
                int threshold = inv.getLowStockThreshold() != null ? inv.getLowStockThreshold() : 5;
                String status = (qty <= 0) ? "OUT_OF_STOCK" : "LOW_STOCK";
                if (qty <= 0) outOfStockCount++; else lowStockCount++;

                String variantInfo = (v.getMetalColor() != null ? v.getMetalColor() : "") + 
                                     (v.getSizeLength() != null && !v.getSizeLength().isEmpty() ? " / " + v.getSizeLength() : "");

                invSb.append("{")
                    .append("\"variantId\":").append(v.getId()).append(",")
                    .append("\"productId\":").append(v.getProduct().getId()).append(",")
                    .append("\"productName\":\"").append(esc(v.getProduct().getName())).append("\",")
                    .append("\"variantInfo\":\"").append(esc(variantInfo.trim())).append("\",")
                    .append("\"sku\":\"").append(esc(v.getSkuVariant())).append("\",")
                    .append("\"quantity\":").append(qty).append(",")
                    .append("\"threshold\":").append(threshold).append(",")
                    .append("\"status\":\"").append(status).append("\"")
                    .append("}");
            }
            invSb.append("]");

            StringBuilder matSb = new StringBuilder("[");
            for (int i = 0; i < matList.size(); i++) {
                if (i > 0) matSb.append(",");
                PackingMaterial m = matList.get(i);
                int qty = m.getQtyInStock() != null ? m.getQtyInStock() : 0;
                int threshold = m.getLowStockThreshold() != null ? m.getLowStockThreshold() : 10;
                String status = (qty <= 0) ? "OUT_OF_STOCK" : "LOW_STOCK";
                if (qty <= 0) outOfStockCount++; else lowStockCount++;

                matSb.append("{")
                    .append("\"id\":").append(m.getId()).append(",")
                    .append("\"name\":\"").append(esc(m.getName())).append("\",")
                    .append("\"quantity\":").append(qty).append(",")
                    .append("\"threshold\":").append(threshold).append(",")
                    .append("\"unitCost\":").append(m.getUnitCost() != null ? m.getUnitCost() : 0).append(",")
                    .append("\"status\":\"").append(status).append("\"")
                    .append("}");
            }
            matSb.append("]");

            int totalAlerts = invList.size() + matList.size();

            StringBuilder res = new StringBuilder("{")
                .append("\"success\":true,")
                .append("\"hasAlerts\":").append(totalAlerts > 0).append(",")
                .append("\"totalAlerts\":").append(totalAlerts).append(",")
                .append("\"outOfStockCount\":").append(outOfStockCount).append(",")
                .append("\"lowStockCount\":").append(lowStockCount).append(",")
                .append("\"productAlertsCount\":").append(invList.size()).append(",")
                .append("\"materialAlertsCount\":").append(matList.size()).append(",")
                .append("\"inventoryAlerts\":").append(invSb).append(",")
                .append("\"materialAlerts\":").append(matSb)
                .append("}");

            return ResponseEntity.ok(res.toString());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── POST /api/admin/inventory ────────────────────────────────────────────
    @PostMapping(value = "/inventory", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> addStock(@RequestParam int variantId, @RequestParam int quantity, HttpServletRequest request) {
        if (!isAdminOrStaff(request)) return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");
        try {
            List<Inventory> invList = em.createQuery(
                    "SELECT i FROM Inventory i WHERE i.productVariant.id = :vid", Inventory.class)
                    .setParameter("vid", variantId).getResultList();
            Inventory inv;
            if (invList.isEmpty()) {
                ProductVariant variant = em.find(ProductVariant.class, variantId);
                if (variant == null) return ResponseEntity.status(404).body("{\"success\":false,\"message\":\"Variant not found.\"}");
                inv = new Inventory();
                inv.setVariantId(variantId);
                inv.setProductVariant(variant);
                inv.setQuantityOnHand(quantity);
                inv.setLowStockThreshold(5);
                em.persist(inv);
            } else {
                inv = invList.get(0);
                int currentQty = inv.getQuantityOnHand() != null ? inv.getQuantityOnHand() : 0;
                inv.setQuantityOnHand(currentQty + quantity);
            }
            em.flush();
            return ResponseEntity.ok("{\"success\":true,\"message\":\"Stock added successfully.\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── PUT /api/admin/inventory/{variantId} ─────────────────────────────────
    @PutMapping(value = "/inventory/{variantId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> updateStock(@PathVariable int variantId, @RequestBody JsonNode body,
                                               HttpServletRequest request) {
        if (!isAdminOrStaff(request)) return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");

        try {
            List<Inventory> invList = em.createQuery(
                    "SELECT i FROM Inventory i WHERE i.productVariant.id = :vid", Inventory.class)
                    .setParameter("vid", variantId).getResultList();

            Inventory inv;
            if (invList.isEmpty()) {
                ProductVariant variant = em.find(ProductVariant.class, variantId);
                if (variant == null) return ResponseEntity.status(404).body("{\"success\":false,\"message\":\"Variant not found.\"}");
                inv = new Inventory();
                inv.setVariantId(variantId);
                inv.setProductVariant(variant);
                inv.setQuantityOnHand(0);
                inv.setLowStockThreshold(5);
                em.persist(inv);
            } else {
                inv = invList.get(0);
            }

            if (!body.path("action").isMissingNode() && !body.path("quantity").isMissingNode()) {
                String action = body.path("action").asText("");
                int qty = body.path("quantity").asInt(0);
                int currentQty = inv.getQuantityOnHand() != null ? inv.getQuantityOnHand() : 0;
                
                String reason = body.path("reason").asText(null);
                if (reason != null && reason.trim().isEmpty()) reason = null;

                HttpSession session = request.getSession(false);
                User adminUser = null;
                if (session != null && session.getAttribute("userId") != null) {
                    adminUser = em.getReference(User.class, session.getAttribute("userId"));
                }

                if ("ADD".equals(action)) {
                    inv.setQuantityOnHand(currentQty + qty);
                    logInventory(inv.getProductVariant(), "ADD", qty, reason, adminUser);
                    try {
                        String primaryImageUrl = "";
                        java.util.List<com.auracraft.entity.ProductImage> primaryImages = em.createQuery("SELECT pi FROM ProductImage pi WHERE pi.product.id = :pid AND pi.isPrimary = true", com.auracraft.entity.ProductImage.class)
                                .setParameter("pid", inv.getProductVariant().getProduct().getId())
                                .getResultList();
                        if (!primaryImages.isEmpty()) {
                            primaryImageUrl = primaryImages.get(0).getImageUrl();
                        }
                        emailService.sendRestockBroadcast(inv.getProductVariant().getProduct(), inv.getProductVariant(), primaryImageUrl);
                        notificationService.broadcastToCustomers("RESTOCK", "Back in Stock: " + inv.getProductVariant().getProduct().getName(), "/product-view.html?id=" + inv.getProductVariant().getProduct().getId());
                        notificationService.notifyStaffByRole(java.util.List.of("ADMIN", "MANAGER", "STOCK_MANAGER"), "RESTOCK", "Restocked: " + inv.getProductVariant().getProduct().getName(), "/admin.html");
                    } catch (Exception ignored) {}
                } else if ("REMOVE".equals(action)) {
                    inv.setQuantityOnHand(Math.max(0, currentQty - qty));
                    logInventory(inv.getProductVariant(), "REMOVE", qty, reason, adminUser);
                } else if ("SET".equals(action)) {
                    inv.setQuantityOnHand(qty);
                    logInventory(inv.getProductVariant(), "SET", qty, reason, adminUser);
                }
            } else if (!body.path("quantityOnHand").isMissingNode()) {
                inv.setQuantityOnHand(body.path("quantityOnHand").asInt());
            }

            if (!body.path("lowStockThreshold").isMissingNode()) inv.setLowStockThreshold(body.path("lowStockThreshold").asInt(5));
            em.merge(inv);

            String vName = inv.getProductVariant() != null && inv.getProductVariant().getProduct() != null ? inv.getProductVariant().getProduct().getName() : "Variant #" + variantId;
            auditLogService.log(request, "UPDATE_INVENTORY", "INVENTORY",
                    "Adjusted stock for " + vName + " (New Qty: " + inv.getQuantityOnHand() + ")",
                    "SUCCESS");

            return ResponseEntity.ok("{\"success\":true,\"message\":\"Inventory updated.\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    private void logInventory(ProductVariant variant, String action, int qty, String reason, User createdBy) {
        InventoryLog log = new InventoryLog();
        log.setProductVariant(variant);
        log.setAction(action);
        log.setQuantity(qty);
        log.setReason(reason);
        log.setCreatedBy(createdBy);
        em.persist(log);
    }

    // ── DELETE /api/admin/inventory/{variantId} ──────────────────────────────
    @DeleteMapping(value = "/inventory/{variantId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> deleteInventoryVariant(@PathVariable int variantId, HttpServletRequest request) {
        if (!isAdminOrStaff(request)) return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");
        try {
            ProductVariant variant = em.find(ProductVariant.class, variantId);
            if (variant == null) return ResponseEntity.status(404).body("{\"success\":false,\"message\":\"Variant not found.\"}");
            
            // Soft delete the variant
            variant.setIsDeleted(true);
            em.merge(variant);
            
            return ResponseEntity.ok("{\"success\":true,\"message\":\"Variant deleted successfully.\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── GET /api/admin/variants?productId=x ──────────────────────────────────
    @GetMapping(value = "/variants", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<String> getVariants(@RequestParam(required = false) Integer productId,
                                               HttpServletRequest request) {
        if (!isAdminOrStaff(request)) return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");

        List<ProductVariant> variants;
        if (productId != null) {
            variants = em.createQuery("SELECT v FROM ProductVariant v WHERE v.product.id = :pid AND (v.isDeleted = false OR v.isDeleted IS NULL) ORDER BY v.id ASC",
                    ProductVariant.class).setParameter("pid", productId).getResultList();
        } else {
            variants = em.createQuery("SELECT v FROM ProductVariant v WHERE (v.isDeleted = false OR v.isDeleted IS NULL) ORDER BY v.product.id ASC, v.id ASC",
                    ProductVariant.class).getResultList();
        }

        StringBuilder arr = new StringBuilder("[");
        for (int i = 0; i < variants.size(); i++) {
            if (i > 0) arr.append(",");
            ProductVariant v = variants.get(i);
            arr.append("{\"id\":").append(v.getId())
                .append(",\"productId\":").append(v.getProduct().getId())
                .append(",\"productName\":\"").append(esc(v.getProduct().getName())).append("\"")
                .append(",\"sku\":\"").append(esc(v.getSkuVariant())).append("\"")
                .append(",\"color\":\"").append(esc(v.getMetalColor())).append("\"")
                .append(",\"size\":\"").append(esc(v.getSizeLength())).append("\"")
                .append(",\"price\":").append(v.getPrice() != null ? v.getPrice() : BigDecimal.ZERO)
                .append(",\"isDeleted\":").append(Boolean.TRUE.equals(v.getIsDeleted()))
                .append("}");
        }
        arr.append("]");
        return ResponseEntity.ok("{\"success\":true,\"variants\":" + arr + "}");
    }

    // ── POST /api/admin/variants ──────────────────────────────────────────────
    @PostMapping(value = "/variants", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createVariant(@RequestBody JsonNode body, HttpServletRequest request) {
        if (!isAdminOrStaff(request)) return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");

        try {
            int productId = body.path("productId").asInt(0);
            String sku = body.path("sku").asText("").trim();
            if (productId <= 0 || sku.isEmpty()) {
                return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"Product ID and SKU are required.\"}");
            }

            Product product = em.find(Product.class, productId);
            if (product == null) return ResponseEntity.status(404).body("{\"success\":false,\"message\":\"Product not found.\"}");

            ProductVariant v = new ProductVariant();
            v.setProduct(product);
            v.setSkuVariant(sku);
            v.setMetalColor(body.path("color").asText("").trim());
            v.setSizeLength(body.path("size").asText("").trim());
            v.setPrice(new BigDecimal(body.path("price").asText("0")));
            if (!body.path("compareAtPrice").isMissingNode())
                v.setCompareAtPrice(new BigDecimal(body.path("compareAtPrice").asText("0")));
            em.persist(v);
            em.flush();

            // Auto-create inventory record
            Inventory inv = new Inventory();
            inv.setProductVariant(v);
            inv.setQuantityOnHand(body.path("quantity").asInt(0));
            inv.setLowStockThreshold(5);
            em.persist(inv);

            return ResponseEntity.ok("{\"success\":true,\"message\":\"Variant created.\",\"id\":" + v.getId() + "}");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }



    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
