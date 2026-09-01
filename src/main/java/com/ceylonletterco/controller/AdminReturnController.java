package com.auracraft.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/admin/returns")
public class AdminReturnController {
    
    @PersistenceContext
    private EntityManager em;

    @org.springframework.beans.factory.annotation.Autowired
    private com.auracraft.service.NotificationService notificationService;

    @org.springframework.beans.factory.annotation.Autowired
    private com.auracraft.service.AuditLogService auditLogService;

    private boolean isStaffOrRep(HttpServletRequest request) {
        jakarta.servlet.http.HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("loggedInUser") == null) return false;
        String role = (String) session.getAttribute("userRole");
        return role != null && !role.equalsIgnoreCase("CUSTOMER");
    }

    // ── GET /api/admin/returns ────────────────────────────────────────────────
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<?> getReturns(HttpServletRequest request) {
        if (!isStaffOrRep(request)) {
            return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied\"}");
        }

        // Fetch Web Returns & Return Requests
        List<com.auracraft.entity.Order> webReturns = em.createQuery(
                "SELECT o FROM Order o WHERE o.orderStatus IN ('RETURNED', 'RETURN_REQUESTED', 'RETURN_APPROVED', 'RETURN_REJECTED') ORDER BY o.createdAt DESC",
                com.auracraft.entity.Order.class).getResultList();
        
        // Fetch POS Returns & Return Requests
        List<com.auracraft.entity.PosOrder> posReturns = em.createQuery(
                "SELECT po FROM PosOrder po WHERE po.orderStatus IN ('RETURNED', 'RETURN_REQUESTED', 'RETURN_APPROVED', 'RETURN_REJECTED') ORDER BY po.createdAt DESC",
                com.auracraft.entity.PosOrder.class).getResultList();
        
        StringBuilder arr = new StringBuilder("[");
        boolean first = true;
        
        for (com.auracraft.entity.Order r : webReturns) {
            if (!first) arr.append(",");
            first = false;
            String fullName = r.getUser() != null ? r.getUser().getFullName() : (r.getShippingAddress() != null ? r.getShippingAddress().getFullName() : "Customer");
            String email = r.getUser() != null ? r.getUser().getEmail() : "";
            String phone = (r.getShippingAddress() != null && r.getShippingAddress().getPhone() != null) ? r.getShippingAddress().getPhone() : (r.getUser() != null && r.getUser().getPhone() != null ? r.getUser().getPhone() : "");
            
            // Build items preview
            StringBuilder itemsSb = new StringBuilder("[");
            List<com.auracraft.entity.OrderItem> items = em.createQuery(
                    "SELECT i FROM OrderItem i WHERE i.order = :ord", com.auracraft.entity.OrderItem.class)
                    .setParameter("ord", r).getResultList();
            for (int k = 0; k < items.size(); k++) {
                com.auracraft.entity.OrderItem it = items.get(k);
                if (k > 0) itemsSb.append(",");
                String pName = it.getProductVariant() != null && it.getProductVariant().getProduct() != null ? it.getProductVariant().getProduct().getName() : "Jewellery Item";
                String color = it.getProductVariant() != null ? it.getProductVariant().getMetalColor() : "";
                String size = it.getProductVariant() != null ? it.getProductVariant().getSizeLength() : "";
                itemsSb.append("{")
                       .append("\"name\":\"").append(esc(pName)).append("\",")
                       .append("\"metalColor\":\"").append(esc(color)).append("\",")
                       .append("\"sizeLength\":\"").append(esc(size)).append("\",")
                       .append("\"qty\":").append(it.getQuantity() != null ? it.getQuantity() : 1).append(",")
                       .append("\"price\":").append(it.getPriceAtPurchase() != null ? it.getPriceAtPurchase() : 0)
                       .append("}");
            }
            itemsSb.append("]");

            arr.append("{")
               .append("\"id\":").append(r.getId()).append(",")
               .append("\"orderType\":\"WEB\",")
               .append("\"orderStatus\":\"").append(esc(r.getOrderStatus() != null ? r.getOrderStatus() : "RETURN_REQUESTED")).append("\",")
               .append("\"paymentStatus\":\"").append(esc(r.getPaymentStatus() != null ? r.getPaymentStatus() : "PENDING")).append("\",")
               .append("\"createdAt\":\"").append(r.getCreatedAt() != null ? r.getCreatedAt().toString() : "").append("\",")
               .append("\"customerName\":\"").append(esc(fullName)).append("\",")
               .append("\"customerEmail\":\"").append(esc(email)).append("\",")
               .append("\"customerPhone\":\"").append(esc(phone)).append("\",")
               .append("\"totalAmount\":").append(r.getTotalAmount() != null ? r.getTotalAmount() : 0).append(",")
               .append("\"returnLoss\":").append(r.getReturnLoss() != null ? r.getReturnLoss() : 0).append(",")
               .append("\"returnReason\":\"").append(esc(r.getReturnReason() != null ? r.getReturnReason() : "")).append("\",")
               .append("\"items\":").append(itemsSb)
               .append("}");
        }

        for (com.auracraft.entity.PosOrder r : posReturns) {
            if (!first) arr.append(",");
            first = false;
            String cust = (r.getCustomerName() != null ? r.getCustomerName() : "POS Customer") + " (POS)";
            String pm = r.getPaymentMethod() != null ? r.getPaymentMethod() : "FULL_PAID";
            String pStatus = "FULL_PAID".equals(pm) ? "PAID" : ("ADVANCE_COD".equals(pm) ? "PARTIAL" : "UNPAID");
            String phone = r.getPhone1() != null ? r.getPhone1() : "";

            // Build pos items preview
            StringBuilder itemsSb = new StringBuilder("[");
            List<com.auracraft.entity.PosOrderItem> items = em.createQuery(
                    "SELECT i FROM PosOrderItem i WHERE i.posOrder = :ord", com.auracraft.entity.PosOrderItem.class)
                    .setParameter("ord", r).getResultList();
            for (int k = 0; k < items.size(); k++) {
                com.auracraft.entity.PosOrderItem it = items.get(k);
                if (k > 0) itemsSb.append(",");
                String pName = it.getProductVariant() != null && it.getProductVariant().getProduct() != null ? it.getProductVariant().getProduct().getName() : "Jewellery Item";
                String color = it.getProductVariant() != null ? it.getProductVariant().getMetalColor() : "";
                String size = it.getProductVariant() != null ? it.getProductVariant().getSizeLength() : "";
                itemsSb.append("{")
                       .append("\"name\":\"").append(esc(pName)).append("\",")
                       .append("\"metalColor\":\"").append(esc(color)).append("\",")
                       .append("\"sizeLength\":\"").append(esc(size)).append("\",")
                       .append("\"qty\":").append(it.getQuantity() != null ? it.getQuantity() : 1).append(",")
                       .append("\"price\":").append(it.getUnitPrice() != null ? it.getUnitPrice() : 0)
                       .append("}");
            }
            itemsSb.append("]");

            arr.append("{")
               .append("\"id\":").append(r.getId()).append(",")
               .append("\"orderType\":\"POS\",")
               .append("\"orderStatus\":\"").append(esc(r.getOrderStatus() != null ? r.getOrderStatus() : "RETURNED")).append("\",")
               .append("\"paymentStatus\":\"").append(esc(pStatus)).append("\",")
               .append("\"createdAt\":\"").append(r.getCreatedAt() != null ? r.getCreatedAt().toString() : "").append("\",")
               .append("\"customerName\":\"").append(esc(cust)).append("\",")
               .append("\"customerEmail\":\"\",")
               .append("\"customerPhone\":\"").append(esc(phone)).append("\",")
               .append("\"totalAmount\":").append(r.getTotalAmount() != null ? r.getTotalAmount() : 0).append(",")
               .append("\"returnLoss\":").append(r.getReturnLoss() != null ? r.getReturnLoss() : 0).append(",")
               .append("\"returnReason\":\"").append(esc(r.getReturnReason() != null ? r.getReturnReason() : "")).append("\",")
               .append("\"items\":").append(itemsSb)
               .append("}");
        }
        arr.append("]");
        
        return ResponseEntity.ok().body("{\"success\":true, \"returns\": " + arr.toString() + "}");
    }

    // ── POST /api/admin/returns/approve ──────────────────────────────────────
    @PostMapping(value = "/approve", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<?> approveReturn(@RequestBody JsonNode body, HttpServletRequest request) {
        if (!isStaffOrRep(request)) {
            return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied\"}");
        }

        try {
            String type = body.path("orderType").asText("WEB");
            int id = body.path("orderId").asInt();
            String instructions = body.path("instructions").asText("").trim();
            if (instructions.isEmpty()) {
                instructions = "Please courier the item in its original box & certificate to: AuraCraft Studio, No. 12, Galle Road, Colombo.";
            }

            if ("WEB".equalsIgnoreCase(type)) {
                com.auracraft.entity.Order order = em.find(com.auracraft.entity.Order.class, id);
                if (order == null) return ResponseEntity.status(404).body("{\"success\":false,\"message\":\"Order not found\"}");
                
                order.setOrderStatus("RETURN_APPROVED");
                String oldReason = order.getReturnReason() != null ? order.getReturnReason() : "";
                order.setReturnReason(oldReason + " | [Approved: " + instructions + "]");
                em.merge(order);

                if (order.getUser() != null && notificationService != null) {
                    notificationService.notifyUser(
                        order.getUser().getId(),
                        "RETURN_APPROVED",
                        "Your return for Order #CLC-" + String.format("%05d", id) + " has been approved! " + instructions,
                        "/account.html"
                    );
                }
            } else {
                com.auracraft.entity.PosOrder posOrder = em.find(com.auracraft.entity.PosOrder.class, id);
                if (posOrder == null) return ResponseEntity.status(404).body("{\"success\":false,\"message\":\"POS Order not found\"}");
                posOrder.setOrderStatus("RETURN_APPROVED");
                String oldReason = posOrder.getReturnReason() != null ? posOrder.getReturnReason() : "";
                posOrder.setReturnReason(oldReason + " | [Approved: " + instructions + "]");
                em.merge(posOrder);
            }

            auditLogService.log(request, "APPROVE_RETURN", "ORDER",
                    "Return approved for " + type + " Order #" + id,
                    "SUCCESS");

            return ResponseEntity.ok("{\"success\":true,\"message\":\"Return request approved successfully! Customer has been notified.\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── POST /api/admin/returns/reject ───────────────────────────────────────
    @PostMapping(value = "/reject", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<?> rejectReturn(@RequestBody JsonNode body, HttpServletRequest request) {
        if (!isStaffOrRep(request)) {
            return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied\"}");
        }

        try {
            String type = body.path("orderType").asText("WEB");
            int id = body.path("orderId").asInt();
            String reason = body.path("rejectionReason").asText("").trim();
            if (reason.isEmpty()) reason = "Item does not meet return policy criteria.";

            if ("WEB".equalsIgnoreCase(type)) {
                com.auracraft.entity.Order order = em.find(com.auracraft.entity.Order.class, id);
                if (order == null) return ResponseEntity.status(404).body("{\"success\":false,\"message\":\"Order not found\"}");
                
                order.setOrderStatus("RETURN_REJECTED");
                String oldReason = order.getReturnReason() != null ? order.getReturnReason() : "";
                order.setReturnReason(oldReason + " | [REJECTED: " + reason + "]");
                em.merge(order);

                if (order.getUser() != null && notificationService != null) {
                    notificationService.notifyUser(
                        order.getUser().getId(),
                        "RETURN_REJECTED",
                        "Your return request for Order #CLC-" + String.format("%05d", id) + " was declined: " + reason,
                        "/account.html"
                    );
                }
            } else {
                com.auracraft.entity.PosOrder posOrder = em.find(com.auracraft.entity.PosOrder.class, id);
                if (posOrder == null) return ResponseEntity.status(404).body("{\"success\":false,\"message\":\"POS Order not found\"}");
                posOrder.setOrderStatus("RETURN_REJECTED");
                String oldReason = posOrder.getReturnReason() != null ? posOrder.getReturnReason() : "";
                posOrder.setReturnReason(oldReason + " | [REJECTED: " + reason + "]");
                em.merge(posOrder);
            }

            auditLogService.log(request, "REJECT_RETURN", "ORDER",
                    "Return rejected for " + type + " Order #" + id + " (Reason: " + reason + ")",
                    "SUCCESS");

            return ResponseEntity.ok("{\"success\":true,\"message\":\"Return request rejected.\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── POST /api/admin/returns/complete ─────────────────────────────────────
    @PostMapping(value = "/complete", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<?> completeReturn(@RequestBody JsonNode body, HttpServletRequest request) {
        if (!isStaffOrRep(request)) {
            return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied\"}");
        }

        try {
            String type = body.path("orderType").asText("WEB");
            int id = body.path("orderId").asInt();
            String condition = body.path("itemCondition").asText("RESELLABLE");
            boolean restock = body.path("restock").asBoolean(true);
            BigDecimal loss = new BigDecimal(body.path("returnLoss").asText("0"));
            String lossCategory = body.path("lossCategory").asText("NONE");
            BigDecimal refundAmount = new BigDecimal(body.path("refundAmount").asText("0"));
            String refundMethod = body.path("refundMethod").asText("ORIGINAL_PAYMENT");
            String notes = body.path("notes").asText("").trim();

            String completionSummary = "[RETURN COMPLETED: Condition=" + condition 
                    + (restock ? " (Restocked)" : " (Scrapped/Loss)") 
                    + " | Refund=Rs." + refundAmount + " (" + refundMethod + ")"
                    + (loss.compareTo(BigDecimal.ZERO) > 0 ? " | Loss=Rs." + loss + " (" + lossCategory + ")" : "")
                    + (notes.isEmpty() ? "" : " | Notes: " + notes) + "]";

            if ("WEB".equalsIgnoreCase(type)) {
                com.auracraft.entity.Order order = em.find(com.auracraft.entity.Order.class, id);
                if (order == null) return ResponseEntity.status(404).body("{\"success\":false,\"message\":\"Order not found\"}");
                
                order.setOrderStatus("RETURNED");
                order.setReturnLoss(loss);
                String oldReason = order.getReturnReason() != null ? order.getReturnReason() : "";
                order.setReturnReason(oldReason + " | " + completionSummary);
                em.merge(order);

                // Optional restock inventory
                if (restock) {
                    List<com.auracraft.entity.OrderItem> items = em.createQuery(
                            "SELECT i FROM OrderItem i WHERE i.order = :ord", com.auracraft.entity.OrderItem.class)
                            .setParameter("ord", order).getResultList();
                    for (com.auracraft.entity.OrderItem it : items) {
                        if (it.getProductVariant() != null) {
                            com.auracraft.entity.Inventory inv = em.find(com.auracraft.entity.Inventory.class, it.getProductVariant().getId());
                            if (inv != null) {
                                inv.setQuantityOnHand(inv.getQuantityOnHand() + (it.getQuantity() != null ? it.getQuantity() : 1));
                                em.merge(inv);
                            }
                        }
                    }
                }

                if (order.getUser() != null && notificationService != null) {
                    notificationService.notifyUser(
                        order.getUser().getId(),
                        "RETURN_COMPLETED",
                        "Your return for Order #CLC-" + String.format("%05d", id) + " has been received & refund of Rs. " + refundAmount + " has been processed.",
                        "/account.html"
                    );
                }
            } else {
                com.auracraft.entity.PosOrder posOrder = em.find(com.auracraft.entity.PosOrder.class, id);
                if (posOrder == null) return ResponseEntity.status(404).body("{\"success\":false,\"message\":\"POS Order not found\"}");
                
                posOrder.setOrderStatus("RETURNED");
                posOrder.setReturnLoss(loss);
                String oldReason = posOrder.getReturnReason() != null ? posOrder.getReturnReason() : "";
                posOrder.setReturnReason(oldReason + " | " + completionSummary);
                em.merge(posOrder);

                if (restock) {
                    List<com.auracraft.entity.PosOrderItem> items = em.createQuery(
                            "SELECT i FROM PosOrderItem i WHERE i.posOrder = :ord", com.auracraft.entity.PosOrderItem.class)
                            .setParameter("ord", posOrder).getResultList();
                    for (com.auracraft.entity.PosOrderItem it : items) {
                        if (it.getProductVariant() != null) {
                            com.auracraft.entity.Inventory inv = em.find(com.auracraft.entity.Inventory.class, it.getProductVariant().getId());
                            if (inv != null) {
                                inv.setQuantityOnHand(inv.getQuantityOnHand() + (it.getQuantity() != null ? it.getQuantity() : 1));
                                em.merge(inv);
                            }
                        }
                    }
                }
            }

            auditLogService.log(request, "COMPLETE_RETURN_REFUND", "ORDER",
                    "Return completed for " + type + " Order #" + id + " | Refund: LKR " + refundAmount + " via " + refundMethod + " | Condition: " + condition + (restock ? " (Restocked)" : " (Loss: LKR " + loss + " - " + lossCategory + ")"),
                    "SUCCESS");

            return ResponseEntity.ok("{\"success\":true,\"message\":\"Return processed and completed successfully! Inventory & loss records updated.\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // Legacy support / general mark
    @PostMapping("/mark")
    @Transactional
    public ResponseEntity<?> markAsReturned(@RequestBody JsonNode body, HttpServletRequest request) {
        if (!isStaffOrRep(request)) {
            return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied\"}");
        }
        
        try {
            String type = body.path("orderType").asText("WEB");
            int id = body.path("orderId").asInt();
            String reason = body.path("returnReason").asText("");
            String targetStatus = body.path("targetStatus").asText("RETURN_REQUESTED");
            BigDecimal loss = new BigDecimal(body.path("returnLoss").asText("0"));

            if ("WEB".equalsIgnoreCase(type)) {
                com.auracraft.entity.Order order = em.find(com.auracraft.entity.Order.class, id);
                if (order == null) return ResponseEntity.status(404).body("{\"success\":false,\"message\":\"Order not found\"}");
                order.setOrderStatus(targetStatus);
                order.setReturnReason(reason);
                if (loss.compareTo(BigDecimal.ZERO) > 0) order.setReturnLoss(loss);
                em.merge(order);
            } else if ("POS".equalsIgnoreCase(type)) {
                com.auracraft.entity.PosOrder posOrder = em.find(com.auracraft.entity.PosOrder.class, id);
                if (posOrder == null) return ResponseEntity.status(404).body("{\"success\":false,\"message\":\"POS Order not found\"}");
                posOrder.setOrderStatus(targetStatus);
                posOrder.setReturnReason(reason);
                if (loss.compareTo(BigDecimal.ZERO) > 0) posOrder.setReturnLoss(loss);
                em.merge(posOrder);
            } else {
                return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"Invalid order type\"}");
            }
            
            try {
                notificationService.notifyStaffByRole(java.util.List.of("ADMIN", "MANAGER", "SUPPORT_OFFICER"), "RETURN_REQUESTED", "Return Logged for Order #" + id + " (" + type + ")", "/admin.html");
            } catch (Exception ignored) {}

            return ResponseEntity.ok().body("{\"success\":true, \"message\":\"Return status updated successfully.\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", "");
    }
}
