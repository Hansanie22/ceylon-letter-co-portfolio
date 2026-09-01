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
 * ShippingController – manages packed orders queue and shipping/tracking.
 * Endpoints: /api/shipping/*
 */
@RestController
@RequestMapping("/api/shipping")
@Transactional
public class ShippingController {

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

    // ── GET /api/shipping/queue — Packed orders ready for shipping ─────────────
    @GetMapping(value = "/queue", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<String> getShippingQueue(HttpServletRequest request) {
        if (getStaffUser(request) == null)
            return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");

        try {
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            StringBuilder sb = new StringBuilder("[");
            int count = 0;

            // Website orders with PACKED status
            List<Order> webOrders = em.createQuery(
                    "SELECT o FROM Order o WHERE o.orderStatus = 'PACKED' ORDER BY o.createdAt ASC", Order.class).getResultList();
            for (Order o : webOrders) {
                if (count > 0) sb.append(",");
                String customerName = o.getShippingAddress() != null ? o.getShippingAddress().getFullName() : (o.getUser() != null ? o.getUser().getFullName() : "N/A");
                String address = o.getShippingAddress() != null ? o.getShippingAddress().getStreet() + ", " + o.getShippingAddress().getCity() : "";
                String phone = o.getShippingAddress() != null ? o.getShippingAddress().getPhone() : (o.getUser() != null ? o.getUser().getPhone() : "");
                sb.append("{")
                    .append("\"source\":\"WEBSITE\",")
                    .append("\"id\":").append(o.getId()).append(",")
                    .append("\"orderCode\":\"CLC-").append(String.format("%05d", o.getId())).append("\",")
                    .append("\"customerName\":\"").append(esc(customerName)).append("\",")
                    .append("\"address\":\"").append(esc(address)).append("\",")
                    .append("\"phone\":\"").append(esc(phone)).append("\",")
                    .append("\"totalAmount\":").append(o.getTotalAmount()).append(",")
                    .append("\"paymentStatus\":\"").append(esc(o.getPaymentStatus())).append("\",")
                    .append("\"trackingNumber\":").append(o.getTrackingNumber() != null ? "\"" + esc(o.getTrackingNumber()) + "\"" : "null").append(",")
                    .append("\"customNotes\":").append(o.getCustomNotes() != null ? "\"" + esc(o.getCustomNotes()) + "\"" : "null").append(",")
                    .append("\"createdAt\":\"").append(o.getCreatedAt() != null ? o.getCreatedAt().format(dtf) : "").append("\"")
                    .append("}");
                count++;
            }

            // POS orders with PACKED status
            List<PosOrder> posOrders = em.createQuery(
                    "SELECT o FROM PosOrder o WHERE o.orderStatus = 'PACKED' ORDER BY o.createdAt ASC", PosOrder.class).getResultList();
            for (PosOrder o : posOrders) {
                if (count > 0) sb.append(",");
                sb.append("{")
                    .append("\"source\":\"POS\",")
                    .append("\"id\":").append(o.getId()).append(",")
                    .append("\"orderCode\":\"POS-").append(String.format("%05d", o.getId())).append("\",")
                    .append("\"customerName\":\"").append(esc(o.getCustomerName())).append("\",")
                    .append("\"address\":\"").append(esc(o.getCustomerAddress())).append("\",")
                    .append("\"phone\":\"").append(esc(o.getPhone1())).append("\",")
                    .append("\"totalAmount\":").append(o.getTotalAmount()).append(",")
                    .append("\"paymentMethod\":\"").append(esc(o.getPaymentMethod())).append("\",")
                    .append("\"trackingNumber\":").append(o.getTrackingNumber() != null ? "\"" + esc(o.getTrackingNumber()) + "\"" : "null").append(",")
                    .append("\"createdAt\":\"").append(o.getCreatedAt() != null ? o.getCreatedAt().format(dtf) : "").append("\"")
                    .append("}");
                count++;
            }

            sb.append("]");
            return ResponseEntity.ok("{\"success\":true,\"pendingCount\":" + count + ",\"orders\":" + sb + "}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── PUT /api/shipping/assign-tracking — Add tracking number ────────────────
    @PutMapping(value = "/assign-tracking", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> assignTracking(@RequestBody JsonNode body, HttpServletRequest request) {
        if (getStaffUser(request) == null)
            return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");

        try {
            String source = body.path("source").asText("POS");
            int orderId = body.path("orderId").asInt();
            String tracking = body.path("trackingNumber").asText("").trim();

            if ("WEBSITE".equals(source)) {
                Order o = em.find(Order.class, orderId);
                if (o == null) return ResponseEntity.status(404).body("{\"success\":false,\"message\":\"Order not found.\"}");
                if (!tracking.isEmpty()) o.setTrackingNumber(tracking);
                o.setOrderStatus("SHIPPED");
                o.setShippedAt(LocalDateTime.now());
                em.merge(o);
            } else {
                PosOrder o = em.find(PosOrder.class, orderId);
                if (o == null) return ResponseEntity.status(404).body("{\"success\":false,\"message\":\"Order not found.\"}");
                if (!tracking.isEmpty()) o.setTrackingNumber(tracking);
                o.setOrderStatus("SHIPPED");
                o.setShippedAt(LocalDateTime.now());
                em.merge(o);
            }

            auditLogService.log(request, "ORDER_SHIPPED", "ORDER",
                    "Order #" + (source.equals("POS") ? "POS-" : "CLC-") + String.format("%05d", orderId) + " (" + source + ") marked as SHIPPED" + (!tracking.isEmpty() ? " with tracking #" + tracking : ""),
                    "SUCCESS");

            return ResponseEntity.ok("{\"success\":true,\"message\":\"Tracking assigned and order marked as shipped.\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── POST /api/shipping/bulk-ship — Bulk mark orders as shipped ─────────────
    @PostMapping(value = "/bulk-ship", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> bulkShip(@RequestBody JsonNode body, HttpServletRequest request) {
        if (getStaffUser(request) == null)
            return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");

        try {
            JsonNode orders = body.path("orders");
            int processed = 0;
            if (orders.isArray()) {
                for (JsonNode item : orders) {
                    String source = item.path("source").asText("POS");
                    int orderId = item.path("orderId").asInt();
                    String tracking = item.path("trackingNumber").asText("").trim();

                    if ("WEBSITE".equals(source)) {
                        Order o = em.find(Order.class, orderId);
                        if (o != null) {
                            if (!tracking.isEmpty()) o.setTrackingNumber(tracking);
                            o.setOrderStatus("SHIPPED");
                            o.setShippedAt(LocalDateTime.now());
                            em.merge(o);
                            processed++;
                        }
                    } else {
                        PosOrder o = em.find(PosOrder.class, orderId);
                        if (o != null) {
                            if (!tracking.isEmpty()) o.setTrackingNumber(tracking);
                            o.setOrderStatus("SHIPPED");
                            o.setShippedAt(LocalDateTime.now());
                            em.merge(o);
                            processed++;
                        }
                    }
                }
            }

            auditLogService.log(request, "BULK_SHIP_ORDERS", "ORDER",
                    "Bulk dispatched " + processed + " orders with tracking",
                    "SUCCESS");

            return ResponseEntity.ok("{\"success\":true,\"processed\":" + processed + "}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── GET /api/shipping/shipped — Recently shipped orders ────────────────────
    @GetMapping(value = "/shipped", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<String> getShippedOrders(HttpServletRequest request) {
        if (getStaffUser(request) == null)
            return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");

        try {
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            StringBuilder sb = new StringBuilder("[");
            int count = 0;

            List<Order> webShipped = em.createQuery(
                    "SELECT o FROM Order o WHERE o.orderStatus = 'SHIPPED' ORDER BY o.shippedAt DESC, o.createdAt DESC", Order.class)
                    .setMaxResults(100).getResultList();
            for (Order o : webShipped) {
                if (count > 0) sb.append(",");
                String customerName = o.getShippingAddress() != null ? o.getShippingAddress().getFullName() : (o.getUser() != null ? o.getUser().getFullName() : "N/A");
                String phone = o.getShippingAddress() != null ? o.getShippingAddress().getPhone() : (o.getUser() != null ? o.getUser().getPhone() : "");
                String shippedTime = o.getShippedAt() != null ? o.getShippedAt().format(dtf) : (o.getCreatedAt() != null ? o.getCreatedAt().format(dtf) : "");
                sb.append("{")
                    .append("\"source\":\"WEBSITE\",")
                    .append("\"id\":").append(o.getId()).append(",")
                    .append("\"orderCode\":\"CLC-").append(String.format("%05d", o.getId())).append("\",")
                    .append("\"customerName\":\"").append(esc(customerName)).append("\",")
                    .append("\"phone\":\"").append(esc(phone)).append("\",")
                    .append("\"trackingNumber\":").append(o.getTrackingNumber() != null ? "\"" + esc(o.getTrackingNumber()) + "\"" : "null").append(",")
                    .append("\"totalAmount\":").append(o.getTotalAmount()).append(",")
                    .append("\"shippedAt\":\"").append(shippedTime).append("\"")
                    .append("}");
                count++;
            }

            List<PosOrder> posShipped = em.createQuery(
                    "SELECT o FROM PosOrder o WHERE o.orderStatus = 'SHIPPED' ORDER BY o.shippedAt DESC, o.createdAt DESC", PosOrder.class)
                    .setMaxResults(100).getResultList();
            for (PosOrder o : posShipped) {
                if (count > 0) sb.append(",");
                sb.append("{")
                    .append("\"source\":\"POS\",")
                    .append("\"id\":").append(o.getId()).append(",")
                    .append("\"orderCode\":\"POS-").append(String.format("%05d", o.getId())).append("\",")
                    .append("\"customerName\":\"").append(esc(o.getCustomerName())).append("\",")
                    .append("\"phone\":\"").append(esc(o.getPhone1())).append("\",")
                    .append("\"trackingNumber\":").append(o.getTrackingNumber() != null ? "\"" + esc(o.getTrackingNumber()) + "\"" : "null").append(",")
                    .append("\"totalAmount\":").append(o.getTotalAmount()).append(",")
                    .append("\"shippedAt\":\"").append(o.getShippedAt() != null ? o.getShippedAt().format(dtf) : "").append("\"")
                    .append("}");
                count++;
            }
            sb.append("]");
            return ResponseEntity.ok("{\"success\":true,\"orders\":" + sb + "}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }
}
