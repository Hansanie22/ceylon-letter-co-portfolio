package com.ceylonletterco.controller;

import com.ceylonletterco.entity.WarrantyClaim;
import com.ceylonletterco.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/warranty-claims")
public class WarrantyClaimController {

    @PersistenceContext
    private EntityManager em;

    @org.springframework.beans.factory.annotation.Autowired
    private com.ceylonletterco.service.NotificationService notificationService;

    @org.springframework.beans.factory.annotation.Autowired
    private com.ceylonletterco.service.AuditLogService auditLogService;

    private boolean isAdminOrStaff(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("loggedInUser") == null) return false;
        String role = (String) session.getAttribute("userRole");
        return role != null && (role.contains("ADMIN") || role.contains("MANAGER") || role.contains("STOCK_MANAGER") || role.contains("SUPPORT_OFFICER") || role.contains("SALES_REP"));
    }

    @PostMapping({"", "/", "/submit"})
    @Transactional
    public ResponseEntity<Map<String, Object>> submitWarrantyClaim(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Map<String, Object> resp = new HashMap<>();
        try {
            if (body == null || !body.containsKey("orderId") || body.get("orderId") == null) {
                resp.put("success", false);
                resp.put("message", "Order ID is required.");
                return ResponseEntity.badRequest().body(resp);
            }

            int orderId = 0;
            String rawId = body.get("orderId").toString().replaceAll("[^0-9]", "");
            if (!rawId.isEmpty()) {
                orderId = Integer.parseInt(rawId);
            }

            String orderType = body.getOrDefault("orderType", "WEB").toString();
            String customerName = body.getOrDefault("customerName", "").toString();
            String customerPhone = body.getOrDefault("customerPhone", "").toString();
            String customerEmail = body.getOrDefault("customerEmail", "").toString();
            String productName = body.getOrDefault("productName", "").toString();
            String claimReason = body.getOrDefault("claimReason", "").toString();
            String proofImageUrl = body.getOrDefault("proofImageUrl", "").toString();

            if (claimReason.isBlank()) {
                resp.put("success", false);
                resp.put("message", "Claim reason is required.");
                return ResponseEntity.badRequest().body(resp);
            }

            // Auto fill missing customer info from user session or Order entity
            HttpSession session = request.getSession(false);
            User sessionUser = (session != null) ? (User) session.getAttribute("loggedInUser") : null;
            if (sessionUser != null) {
                if (customerName.isBlank() && sessionUser.getFullName() != null) customerName = sessionUser.getFullName();
                if (customerEmail.isBlank() && sessionUser.getEmail() != null) customerEmail = sessionUser.getEmail();
                if (customerPhone.isBlank() && sessionUser.getPhone() != null) customerPhone = sessionUser.getPhone();
            }

            if ("WEB".equalsIgnoreCase(orderType) && orderId > 0) {
                com.ceylonletterco.entity.Order ord = em.find(com.ceylonletterco.entity.Order.class, orderId);
                if (ord != null) {
                    if (customerName.isBlank() && ord.getUser() != null) customerName = ord.getUser().getFullName();
                    if (customerEmail.isBlank() && ord.getUser() != null) customerEmail = ord.getUser().getEmail();
                    if (customerPhone.isBlank() && ord.getUser() != null) customerPhone = ord.getUser().getPhone();
                    if (customerPhone.isBlank() && ord.getShippingAddress() != null) customerPhone = ord.getShippingAddress().getPhone();
                }
            }

            WarrantyClaim claim = new WarrantyClaim();
            claim.setOrderId(orderId);
            claim.setOrderType(orderType);
            claim.setCustomerName(customerName);
            claim.setCustomerPhone(customerPhone);
            claim.setCustomerEmail(customerEmail);
            claim.setProductName(productName.isBlank() ? "Jewellery Item" : productName);
            claim.setClaimReason(claimReason);
            claim.setProofImageUrl(proofImageUrl);
            claim.setClaimStatus("PENDING_REVIEW");

            em.persist(claim);

            if (notificationService != null) {
                notificationService.notifyStaffByRole(
                    List.of("ADMIN", "MANAGER", "SUPPORT_OFFICER"),
                    "WARRANTY_CLAIM",
                    "Warranty Claim filed for Order #" + (orderType.equals("POS") ? "POS-" : "CLC-") + String.format("%05d", orderId) + " (" + claim.getProductName() + ")",
                    "/admin.html"
                );
            }

            auditLogService.log(request, "SUBMIT_WARRANTY_CLAIM", "SUPPORT",
                    "Warranty Claim #" + claim.getId() + " filed for Order #" + (orderType.equals("POS") ? "POS-" : "CLC-") + String.format("%05d", orderId) + " (" + claim.getProductName() + ") by " + (customerEmail.isBlank() ? customerPhone : customerEmail),
                    "SUCCESS");

            resp.put("success", true);
            resp.put("message", "Warranty claim request submitted successfully.");
            resp.put("claimId", claim.getId());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("message", "Error submitting warranty claim: " + e.getMessage());
            return ResponseEntity.internalServerError().body(resp);
        }
    }

    @GetMapping("/my")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getMyWarrantyClaims(HttpServletRequest request) {
        Map<String, Object> resp = new HashMap<>();
        HttpSession session = request.getSession(false);
        User user = (session != null) ? (User) session.getAttribute("loggedInUser") : null;
        if (user == null) {
            resp.put("success", false);
            resp.put("message", "Not authenticated");
            return ResponseEntity.status(401).body(resp);
        }

        try {
            List<Integer> userOrderIds = em.createQuery("SELECT o.id FROM Order o WHERE o.user.id = :uid", Integer.class)
                    .setParameter("uid", user.getId())
                    .getResultList();

            String hql = "SELECT wc FROM WarrantyClaim wc WHERE wc.customerEmail = :email ";
            if (user.getPhone() != null && !user.getPhone().isBlank()) {
                hql += "OR wc.customerPhone = :phone ";
            }
            if (!userOrderIds.isEmpty()) {
                hql += "OR (wc.orderType = 'WEB' AND wc.orderId IN :oids) ";
            }
            hql += "ORDER BY wc.createdAt DESC";

            var query = em.createQuery(hql, WarrantyClaim.class)
                    .setParameter("email", user.getEmail());
            if (user.getPhone() != null && !user.getPhone().isBlank()) {
                query.setParameter("phone", user.getPhone());
            }
            if (!userOrderIds.isEmpty()) {
                query.setParameter("oids", userOrderIds);
            }

            List<WarrantyClaim> list = query.getResultList();
            resp.put("success", true);
            resp.put("claims", list);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("message", "Error fetching user warranty claims: " + e.getMessage());
            return ResponseEntity.internalServerError().body(resp);
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getWarrantyClaims(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            HttpServletRequest request) {

        Map<String, Object> resp = new HashMap<>();
        if (!isAdminOrStaff(request)) {
            resp.put("success", false);
            resp.put("message", "Access denied.");
            return ResponseEntity.status(403).body(resp);
        }

        LocalDateTime s = (start != null && !start.isBlank()) ? LocalDate.parse(start).atStartOfDay() : LocalDateTime.now().minusYears(5);
        LocalDateTime e = (end != null && !end.isBlank()) ? LocalDate.parse(end).atTime(23, 59, 59) : LocalDateTime.now();

        String hql = "SELECT wc FROM WarrantyClaim wc WHERE wc.createdAt BETWEEN :s AND :e ";
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            hql += "AND wc.claimStatus = :st ";
        }
        hql += "ORDER BY wc.createdAt DESC";

        var query = em.createQuery(hql, WarrantyClaim.class)
                .setParameter("s", s)
                .setParameter("e", e);

        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            query.setParameter("st", status);
        }

        List<WarrantyClaim> list = query.getResultList();

        resp.put("success", true);
        resp.put("claims", list);
        return ResponseEntity.ok(resp);
    }

    @PutMapping("/{id}/status")
    @Transactional
    public ResponseEntity<Map<String, Object>> updateClaimStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        Map<String, Object> resp = new HashMap<>();
        if (!isAdminOrStaff(request)) {
            resp.put("success", false);
            resp.put("message", "Access denied.");
            return ResponseEntity.status(403).body(resp);
        }

        try {
            WarrantyClaim claim = em.find(WarrantyClaim.class, id);
            if (claim == null) {
                resp.put("success", false);
                resp.put("message", "Warranty claim not found.");
                return ResponseEntity.status(404).body(resp);
            }

            String newStatus = body.getOrDefault("status", claim.getClaimStatus()).toString();
            String notes = body.containsKey("adminNotes") ? body.get("adminNotes").toString() : claim.getAdminNotes();

            claim.setClaimStatus(newStatus);
            claim.setAdminNotes(notes);
            em.merge(claim);

            auditLogService.log(request, "UPDATE_WARRANTY_STATUS", "SUPPORT",
                    "Warranty Claim #" + id + " status updated to " + newStatus + (notes != null && !notes.isBlank() ? " (Notes: " + notes + ")" : ""),
                    "SUCCESS");

            resp.put("success", true);
            resp.put("message", "Warranty claim status updated to " + newStatus);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("message", "Failed to update claim: " + e.getMessage());
            return ResponseEntity.internalServerError().body(resp);
        }
    }
}
