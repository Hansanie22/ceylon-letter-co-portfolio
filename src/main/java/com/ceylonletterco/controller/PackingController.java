package com.ceylonletterco.controller;

import com.ceylonletterco.entity.*;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * PackingController – manages packing queue, packing material sets, and marking orders as packed.
 * Endpoints: /api/packing/*
 */
@RestController
@RequestMapping("/api/packing")
@Transactional
public class PackingController {

    @PersistenceContext
    private EntityManager em;

    @org.springframework.beans.factory.annotation.Autowired
    private com.ceylonletterco.service.AuditLogService auditLogService;

    private User getStaffUser(HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        if (s == null) return null;
        User u = (User) s.getAttribute("loggedInUser");
        if (u == null) return null;
        String role = u.getRole() != null ? u.getRole().toUpperCase() : "";
        if (!"CUSTOMER".equals(role) && !role.isEmpty()) return u;
        return null;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    // ── GET /api/packing/queue — Orders ready to pack ──────────────────────────
    @GetMapping(value = "/queue", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<String> getPackingQueue(HttpServletRequest request) {
        if (getStaffUser(request) == null)
            return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");

        try {
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            StringBuilder sb = new StringBuilder("[");
            int count = 0;

            // Website orders with PROCESSING status ONLY
            List<Order> webOrders = em.createQuery(
                    "SELECT o FROM Order o WHERE o.orderStatus = 'PROCESSING' ORDER BY o.createdAt ASC", Order.class)
                    .getResultList();
            for (Order o : webOrders) {
                if (count > 0) sb.append(",");
                List<OrderItem> items = em.createQuery(
                        "SELECT oi FROM OrderItem oi JOIN FETCH oi.productVariant v JOIN FETCH v.product WHERE oi.order.id = :oid",
                        OrderItem.class).setParameter("oid", o.getId()).getResultList();

                String customerName = o.getShippingAddress() != null ? o.getShippingAddress().getFullName() : (o.getUser() != null ? o.getUser().getFullName() : "N/A");
                String phone1 = o.getShippingAddress() != null ? o.getShippingAddress().getPhone() : (o.getUser() != null ? o.getUser().getPhone() : "");
                String phone2 = (o.getUser() != null && o.getShippingAddress() != null && !o.getUser().getPhone().equals(o.getShippingAddress().getPhone())) ? o.getUser().getPhone() : "";
                String customerAddr = o.getShippingAddress() != null ? (o.getShippingAddress().getStreet() + ", " + o.getShippingAddress().getCity() + (o.getShippingAddress().getDistrict() != null ? ", " + o.getShippingAddress().getDistrict() : "")) : "";

                sb.append("{")
                    .append("\"source\":\"WEBSITE\",")
                    .append("\"id\":").append(o.getId()).append(",")
                    .append("\"orderCode\":\"CLC-").append(String.format("%05d", o.getId())).append("\",")
                    .append("\"customerName\":\"").append(esc(customerName)).append("\",")
                    .append("\"customerAddress\":\"").append(esc(customerAddr)).append("\",")
                    .append("\"phone1\":\"").append(esc(phone1)).append("\",")
                    .append("\"phone2\":\"").append(esc(phone2)).append("\",")
                    .append("\"status\":\"").append(esc(o.getOrderStatus())).append("\",")
                    .append("\"totalAmount\":").append(o.getTotalAmount()).append(",")
                    .append("\"customNotes\":\"").append(esc(o.getCustomNotes())).append("\",")
                    .append("\"createdAt\":\"").append(o.getCreatedAt() != null ? o.getCreatedAt().format(dtf) : "").append("\",")
                    .append("\"items\":[");
                appendItems(sb, items);
                sb.append("]}");
                count++;
            }

            // POS orders with PROCESSING status ONLY
            List<PosOrder> posOrders = em.createQuery(
                    "SELECT o FROM PosOrder o WHERE o.orderStatus = 'PROCESSING' ORDER BY o.createdAt ASC", PosOrder.class)
                    .getResultList();
            for (PosOrder o : posOrders) {
                if (count > 0) sb.append(",");
                List<PosOrderItem> items = em.createQuery(
                        "SELECT oi FROM PosOrderItem oi JOIN FETCH oi.productVariant v JOIN FETCH v.product WHERE oi.posOrder.id = :oid",
                        PosOrderItem.class).setParameter("oid", o.getId()).getResultList();

                sb.append("{")
                    .append("\"source\":\"POS\",")
                    .append("\"id\":").append(o.getId()).append(",")
                    .append("\"orderCode\":\"POS-").append(String.format("%05d", o.getId())).append("\",")
                    .append("\"customerName\":\"").append(esc(o.getCustomerName())).append("\",")
                    .append("\"customerAddress\":\"").append(esc(o.getCustomerAddress())).append("\",")
                    .append("\"phone1\":\"").append(esc(o.getPhone1())).append("\",")
                    .append("\"phone2\":\"").append(esc(o.getPhone2())).append("\",")
                    .append("\"status\":\"").append(esc(o.getOrderStatus())).append("\",")
                    .append("\"totalAmount\":").append(o.getTotalAmount()).append(",")
                    .append("\"isCustom\":").append(o.getIsCustom() != null && o.getIsCustom()).append(",")
                    .append("\"customNotes\":\"").append(esc(o.getCustomNotes())).append("\",")
                    .append("\"createdAt\":\"").append(o.getCreatedAt() != null ? o.getCreatedAt().format(dtf) : "").append("\",")
                    .append("\"items\":[");
                appendPosItems(sb, items);
                sb.append("]}");
                count++;
            }

            sb.append("]");

            // Count packed today
            LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
            Long packedTodayWeb = em.createQuery("SELECT COUNT(o) FROM Order o WHERE o.orderStatus = 'PACKED' AND o.createdAt >= :start", Long.class)
                    .setParameter("start", startOfDay).getSingleResult();
            Long packedTodayPos = em.createQuery("SELECT COUNT(o) FROM PosOrder o WHERE o.orderStatus = 'PACKED' AND o.packedAt >= :start", Long.class)
                    .setParameter("start", startOfDay).getSingleResult();

            return ResponseEntity.ok("{\"success\":true,\"pendingCount\":" + count +
                    ",\"packedToday\":" + (packedTodayWeb + packedTodayPos) +
                    ",\"orders\":" + sb + "}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    private void appendItems(StringBuilder sb, List<OrderItem> items) {
        for (int j = 0; j < items.size(); j++) {
            if (j > 0) sb.append(",");
            OrderItem oi = items.get(j);
            sb.append("{")
                .append("\"productName\":\"").append(esc(oi.getProductVariant().getProduct().getName())).append("\",")
                .append("\"variant\":\"").append(esc(oi.getProductVariant().getMetalColor())).append(" / ").append(esc(oi.getProductVariant().getSizeLength())).append("\",")
                .append("\"quantity\":").append(oi.getQuantity()).append(",")
                .append("\"engravingText\":\"").append(esc(oi.getEngravingText())).append("\",")
                .append("\"customResize\":\"").append(esc(oi.getCustomResize())).append("\"")
                .append("}");
        }
    }

    private void appendPosItems(StringBuilder sb, List<PosOrderItem> items) {
        for (int j = 0; j < items.size(); j++) {
            if (j > 0) sb.append(",");
            PosOrderItem oi = items.get(j);
            sb.append("{")
                .append("\"productName\":\"").append(esc(oi.getProductVariant().getProduct().getName())).append("\",")
                .append("\"variant\":\"").append(esc(oi.getProductVariant().getMetalColor())).append(" / ").append(esc(oi.getProductVariant().getSizeLength())).append("\",")
                .append("\"quantity\":").append(oi.getQuantity()).append(",")
                .append("\"engravingText\":\"").append(esc(oi.getEngravingText())).append("\",")
                .append("\"customResize\":\"").append(esc(oi.getCustomResize())).append("\"")
                .append("}");
        }
    }

    // ── GET /api/packing/material-sets — Get all packing material sets ─────────
    @GetMapping(value = "/material-sets", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<String> getMaterialSets(HttpServletRequest request) {
        if (getStaffUser(request) == null)
            return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");

        List<PackingMaterialSet> sets = em.createQuery("SELECT s FROM PackingMaterialSet s ORDER BY s.setName ASC", PackingMaterialSet.class).getResultList();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < sets.size(); i++) {
            if (i > 0) sb.append(",");
            PackingMaterialSet s = sets.get(i);
            List<PackingSetItem> items = em.createQuery("SELECT si FROM PackingSetItem si JOIN FETCH si.material WHERE si.materialSet.id = :sid", PackingSetItem.class)
                    .setParameter("sid", s.getId()).getResultList();
            sb.append("{\"id\":").append(s.getId())
              .append(",\"setName\":\"").append(esc(s.getSetName()))
              .append("\",\"description\":\"").append(esc(s.getDescription()))
              .append("\",\"items\":[");
            for (int j = 0; j < items.size(); j++) {
                if (j > 0) sb.append(",");
                PackingSetItem si = items.get(j);
                sb.append("{\"materialId\":").append(si.getMaterial().getId())
                  .append(",\"materialName\":\"").append(esc(si.getMaterial().getName()))
                  .append("\",\"qtyUsed\":").append(si.getQtyUsed())
                  .append(",\"qtyInStock\":").append(si.getMaterial().getQtyInStock())
                  .append("}");
            }
            sb.append("]}");
        }
        sb.append("]");
        return ResponseEntity.ok("{\"success\":true,\"sets\":" + sb + "}");
    }

    // ── GET /api/packing/materials — Get all packing materials ────────────────
    @GetMapping(value = "/materials", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<String> getMaterials(HttpServletRequest request) {
        if (getStaffUser(request) == null)
            return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");

        List<PackingMaterial> mats = em.createQuery("SELECT m FROM PackingMaterial m ORDER BY m.name ASC", PackingMaterial.class).getResultList();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < mats.size(); i++) {
            if (i > 0) sb.append(",");
            PackingMaterial m = mats.get(i);
            sb.append("{\"id\":").append(m.getId())
              .append(",\"name\":\"").append(esc(m.getName()))
              .append("\",\"qtyInStock\":").append(m.getQtyInStock())
              .append(",\"lowStockThreshold\":").append(m.getLowStockThreshold())
              .append("}");
        }
        sb.append("]");
        return ResponseEntity.ok("{\"success\":true,\"materials\":" + sb + "}");
    }

    // ── POST /api/packing/materials — Add material ────────────────────────────
    @PostMapping(value = "/materials", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> addMaterial(@RequestBody JsonNode body, HttpServletRequest request) {
        if (getStaffUser(request) == null)
            return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");

        PackingMaterial m = new PackingMaterial();
        m.setName(body.path("name").asText());
        m.setQtyInStock(body.path("qtyInStock").asInt(0));
        m.setLowStockThreshold(body.path("lowStockThreshold").asInt(10));
        if (body.has("unitCost")) {
            m.setUnitCost(new java.math.BigDecimal(body.path("unitCost").asText("0")));
        }
        em.persist(m);
        return ResponseEntity.ok("{\"success\":true,\"id\":" + m.getId() + "}");
    }

    // ── PUT /api/packing/materials/{id}/stock — Restock material ─────────────
    @PutMapping(value = "/materials/{id}/stock", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> restockMaterial(@PathVariable int id, @RequestBody JsonNode body, HttpServletRequest request) {
        if (getStaffUser(request) == null)
            return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");

        PackingMaterial m = em.find(PackingMaterial.class, id);
        if (m == null) return ResponseEntity.status(404).body("{\"success\":false,\"message\":\"Not found.\"}");
        int add = body.path("qty").asInt(0);
        m.setQtyInStock(m.getQtyInStock() + add);
        em.merge(m);
        
        PackingMaterialLog log = new PackingMaterialLog();
        log.setPackingMaterial(m);
        log.setCreatedBy(getStaffUser(request));
        log.setAction("RESTOCK");
        log.setQuantityChange(add);
        log.setReason("Restocked manually");
        em.persist(log);

        return ResponseEntity.ok("{\"success\":true,\"newQty\":" + m.getQtyInStock() + "}");
    }

    // ── POST /api/packing/materials/{id}/adjust — Adjust stock with reason ────
    @PostMapping(value = "/materials/{id}/adjust", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> adjustMaterialStock(@PathVariable int id, @RequestBody JsonNode body, HttpServletRequest request) {
        if (getStaffUser(request) == null)
            return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");

        PackingMaterial m = em.find(PackingMaterial.class, id);
        if (m == null) return ResponseEntity.status(404).body("{\"success\":false,\"message\":\"Not found.\"}");
        
        int change = body.path("quantityChange").asInt(0);
        String reason = body.path("reason").asText("");
        
        m.setQtyInStock(Math.max(0, m.getQtyInStock() + change));
        em.merge(m);
        
        PackingMaterialLog log = new PackingMaterialLog();
        log.setPackingMaterial(m);
        log.setCreatedBy(getStaffUser(request));
        log.setAction(change >= 0 ? "CORRECTION" : "REMOVED");
        log.setQuantityChange(change);
        log.setReason(reason);
        em.persist(log);

        return ResponseEntity.ok("{\"success\":true,\"newQty\":" + m.getQtyInStock() + "}");
    }

    // ── POST /api/packing/material-sets — Create packing set ─────────────────
    @PostMapping(value = "/material-sets", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createSet(@RequestBody JsonNode body, HttpServletRequest request) {
        if (getStaffUser(request) == null)
            return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");

        PackingMaterialSet set = new PackingMaterialSet();
        set.setSetName(body.path("setName").asText());
        set.setDescription(body.path("description").asText(""));
        em.persist(set);
        em.flush();

        JsonNode items = body.path("items");
        if (items.isArray()) {
            for (JsonNode item : items) {
                int matId = item.path("materialId").asInt();
                int qty = item.path("qtyUsed").asInt(1);
                PackingMaterial mat = em.find(PackingMaterial.class, matId);
                if (mat == null) continue;
                PackingSetItem si = new PackingSetItem();
                si.setMaterialSet(set);
                si.setMaterial(mat);
                si.setQtyUsed(qty);
                em.persist(si);
            }
        }
        return ResponseEntity.ok("{\"success\":true,\"id\":" + set.getId() + "}");
    }

    // ── POST /api/packing/mark-packed — Mark order as packed and deduct materials
    @PostMapping(value = "/mark-packed", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> markPacked(@RequestBody JsonNode body, HttpServletRequest request) {
        User staff = getStaffUser(request);
        if (staff == null)
            return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");

        try {
            String source = body.path("source").asText("POS"); // WEBSITE or POS
            int orderId = body.path("orderId").asInt();
            int setId = body.path("setId").asInt();
            int setQty = body.path("setQty").asInt(1);

            // Update order status
            LocalDateTime now = LocalDateTime.now();
            if ("WEBSITE".equals(source)) {
                Order o = em.find(Order.class, orderId);
                if (o == null) return ResponseEntity.status(404).body("{\"success\":false,\"message\":\"Order not found.\"}");
                o.setOrderStatus("PACKED");
                em.merge(o);
            } else {
                PosOrder o = em.find(PosOrder.class, orderId);
                if (o == null) return ResponseEntity.status(404).body("{\"success\":false,\"message\":\"Order not found.\"}");
                o.setOrderStatus("PACKED");
                o.setPackedAt(now);
                em.merge(o);
            }

            // Deduct packing materials and calculate packing cost
            java.math.BigDecimal packingCost = java.math.BigDecimal.ZERO;
            if (setId > 0) {
                List<PackingSetItem> setItems = em.createQuery(
                        "SELECT si FROM PackingSetItem si JOIN FETCH si.material WHERE si.materialSet.id = :sid",
                        PackingSetItem.class).setParameter("sid", setId).getResultList();
                for (PackingSetItem si : setItems) {
                    PackingMaterial mat = si.getMaterial();
                    int totalDeduction = si.getQtyUsed() * setQty;
                    mat.setQtyInStock(Math.max(0, mat.getQtyInStock() - totalDeduction));
                    em.merge(mat);
                    
                    if (mat.getUnitCost() != null) {
                        packingCost = packingCost.add(mat.getUnitCost().multiply(java.math.BigDecimal.valueOf(totalDeduction)));
                    }
                    
                    PackingMaterialLog log = new PackingMaterialLog();
                    log.setPackingMaterial(mat);
                    log.setCreatedBy(staff);
                    log.setAction("PACKED");
                    log.setQuantityChange(-totalDeduction);
                    log.setReason("Packed Order: " + orderId + " (" + source + ")");
                    em.persist(log);
                }
            }

            // Update total order cost (add packing cost)
            if ("WEBSITE".equals(source)) {
                Order o = em.find(Order.class, orderId);
                if (o != null) {
                    if (o.getTotalCost() == null) o.setTotalCost(java.math.BigDecimal.ZERO);
                    o.setTotalCost(o.getTotalCost().add(packingCost));
                    em.merge(o);
                }
            } else {
                PosOrder o = em.find(PosOrder.class, orderId);
                if (o != null) {
                    if (o.getTotalCost() == null) o.setTotalCost(java.math.BigDecimal.ZERO);
                    o.setTotalCost(o.getTotalCost().add(packingCost));
                    em.merge(o);
                }
            }

            auditLogService.log(request, "ORDER_PACKED", "ORDER",
                    "Order #" + (source.equals("POS") ? "POS-" : "CLC-") + String.format("%05d", orderId) + " (" + source + ") marked as PACKED by " + staff.getEmail(),
                    "SUCCESS");

            return ResponseEntity.ok("{\"success\":true,\"message\":\"Order marked as packed.\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── PUT /api/packing/materials/{id} — Edit Packing Material ────────────────
    @PutMapping(value = "/materials/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> updateMaterial(@PathVariable int id, @RequestBody JsonNode body, HttpServletRequest request) {
        if (getStaffUser(request) == null)
            return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");

        PackingMaterial m = em.find(PackingMaterial.class, id);
        if (m == null) return ResponseEntity.status(404).body("{\"success\":false,\"message\":\"Not found.\"}");

        if (body.has("name")) m.setName(body.path("name").asText(m.getName()));
        if (body.has("unitCost")) m.setUnitCost(new java.math.BigDecimal(body.path("unitCost").asText("0")));
        if (body.has("lowStockThreshold")) m.setLowStockThreshold(body.path("lowStockThreshold").asInt(m.getLowStockThreshold()));
        if (body.has("qtyInStock")) m.setQtyInStock(body.path("qtyInStock").asInt(m.getQtyInStock()));
        em.merge(m);

        return ResponseEntity.ok("{\"success\":true,\"message\":\"Material updated successfully.\"}");
    }

    // ── DELETE /api/packing/materials/{id} — Delete Packing Material ───────────
    @DeleteMapping(value = "/materials/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> deleteMaterial(@PathVariable int id, HttpServletRequest request) {
        if (getStaffUser(request) == null)
            return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");

        PackingMaterial m = em.find(PackingMaterial.class, id);
        if (m == null) return ResponseEntity.status(404).body("{\"success\":false,\"message\":\"Not found.\"}");

        em.createQuery("DELETE FROM PackingSetItem si WHERE si.material.id = :mid").setParameter("mid", id).executeUpdate();
        em.createQuery("DELETE FROM PackingMaterialLog log WHERE log.packingMaterial.id = :mid").setParameter("mid", id).executeUpdate();
        em.remove(m);

        return ResponseEntity.ok("{\"success\":true,\"message\":\"Material deleted successfully.\"}");
    }

    // ── PUT /api/packing/material-sets/{id} — Edit Packing Set ───────────────
    @PutMapping(value = "/material-sets/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> updateSet(@PathVariable int id, @RequestBody JsonNode body, HttpServletRequest request) {
        if (getStaffUser(request) == null)
            return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");

        PackingMaterialSet set = em.find(PackingMaterialSet.class, id);
        if (set == null) return ResponseEntity.status(404).body("{\"success\":false,\"message\":\"Not found.\"}");

        if (body.has("setName")) set.setSetName(body.path("setName").asText(set.getSetName()));
        if (body.has("description")) set.setDescription(body.path("description").asText(set.getDescription()));
        em.merge(set);

        if (body.has("items")) {
            em.createQuery("DELETE FROM PackingSetItem si WHERE si.materialSet.id = :sid").setParameter("sid", id).executeUpdate();
            JsonNode items = body.path("items");
            if (items.isArray()) {
                for (JsonNode item : items) {
                    int matId = item.path("materialId").asInt();
                    int qty = item.path("qtyUsed").asInt(1);
                    PackingMaterial mat = em.find(PackingMaterial.class, matId);
                    if (mat == null) continue;
                    PackingSetItem si = new PackingSetItem();
                    si.setMaterialSet(set);
                    si.setMaterial(mat);
                    si.setQtyUsed(qty);
                    em.persist(si);
                }
            }
        }
        return ResponseEntity.ok("{\"success\":true,\"message\":\"Set updated successfully.\"}");
    }

    // ── DELETE /api/packing/material-sets/{id} — Delete Packing Set ───────────
    @DeleteMapping(value = "/material-sets/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> deleteSet(@PathVariable int id, HttpServletRequest request) {
        if (getStaffUser(request) == null)
            return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");

        PackingMaterialSet set = em.find(PackingMaterialSet.class, id);
        if (set == null) return ResponseEntity.status(404).body("{\"success\":false,\"message\":\"Not found.\"}");

        em.createQuery("DELETE FROM PackingSetItem si WHERE si.materialSet.id = :sid").setParameter("sid", id).executeUpdate();
        em.remove(set);

        return ResponseEntity.ok("{\"success\":true,\"message\":\"Material set deleted successfully.\"}");
    }
}
