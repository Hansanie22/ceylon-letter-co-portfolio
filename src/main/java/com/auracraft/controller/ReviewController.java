package com.auracraft.controller;

import com.auracraft.entity.User;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * ReviewController – migrated from ReviewServlet.
 * Handles /api/reviews/* endpoints.
 */
@RestController
@RequestMapping("/api/reviews")
@Transactional
public class ReviewController {

    @PersistenceContext
    private EntityManager em;

    // ── GET /api/reviews?productId=x ────────────────────────────────────────
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<String> getReviews(@RequestParam(required = false) Integer productId,
            @RequestParam(required = false, defaultValue = "5") Integer limit,
            HttpServletRequest request) {
        try {
            int max = (limit != null && limit > 0 && limit <= 50) ? limit : 5;
            List<Object[]> rows;
            if (productId != null) {
                rows = em.createNativeQuery(
                        "SELECT r.id, r.rating, r.comment, r.created_at, u.full_name, p.id AS product_id, p.name AS product_name, pv.metal_color, pv.size_length "
                                +
                                "FROM reviews r " +
                                "JOIN users u ON r.user_id = u.id " +
                                "JOIN product_variants pv ON r.variant_id = pv.id " +
                                "JOIN products p ON pv.product_id = p.id " +
                                "WHERE pv.product_id = ? ORDER BY r.created_at DESC")
                        .setParameter(1, productId)
                        .getResultList();
            } else {
                rows = em.createNativeQuery(
                        "SELECT r.id, r.rating, r.comment, r.created_at, u.full_name, p.id AS product_id, p.name AS product_name, pv.metal_color, pv.size_length "
                                +
                                "FROM reviews r " +
                                "JOIN users u ON r.user_id = u.id " +
                                "LEFT JOIN product_variants pv ON r.variant_id = pv.id " +
                                "LEFT JOIN products p ON pv.product_id = p.id " +
                                "ORDER BY r.created_at DESC LIMIT " + max)
                        .getResultList();
            }

            StringBuilder arr = new StringBuilder("[");
            for (int i = 0; i < rows.size(); i++) {
                Object[] row = rows.get(i);
                if (i > 0)
                    arr.append(",");
                int pid = (row[5] != null) ? ((Number) row[5]).intValue() : 0;
                String primaryImg = (pid > 0) ? getPrimaryImage(pid) : "";
                String prodName = (row[6] != null) ? row[6].toString() : "";
                String color = (row[7] != null) ? row[7].toString() : "";
                String size = (row[8] != null) ? row[8].toString() : "";
                String uName = (row[4] != null) ? row[4].toString() : "Customer";
                String dt = (row[3] != null) ? row[3].toString().substring(0, 10) : "";

                arr.append("{")
                        .append("\"id\":").append(row[0]).append(",")
                        .append("\"rating\":").append(row[1]).append(",")
                        .append("\"comment\":\"").append(esc(row[2] != null ? row[2].toString() : "")).append("\",")
                        .append("\"date\":\"").append(dt).append("\",")
                        .append("\"createdAt\":\"").append(dt).append("\",")
                        .append("\"userName\":\"").append(esc(uName)).append("\",")
                        .append("\"reviewerName\":\"").append(esc(uName)).append("\",")
                        .append("\"productId\":").append(pid).append(",")
                        .append("\"productName\":\"").append(esc(prodName)).append("\",")
                        .append("\"metalColor\":\"").append(esc(color)).append("\",")
                        .append("\"sizeLength\":\"").append(esc(size)).append("\",")
                        .append("\"imageUrl\":\"").append(esc(primaryImg)).append("\"")
                        .append("}");
            }
            arr.append("]");
            return ResponseEntity.ok("{\"success\":true,\"reviews\":" + arr + "}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    @GetMapping(value = "/my", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<String> getMyReviews(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        User user = (session != null) ? (User) session.getAttribute("loggedInUser") : null;
        if (user == null)
            return ResponseEntity.status(401).body("{\"success\":false,\"message\":\"Not authenticated\"}");

        try {
            List<Object[]> rows = em.createNativeQuery(
                    "SELECT r.id, r.rating, r.comment, r.created_at, p.id AS product_id, p.name AS product_name, pv.metal_color, pv.size_length "
                            +
                            "FROM reviews r " +
                            "JOIN product_variants pv ON r.variant_id = pv.id " +
                            "JOIN products p ON pv.product_id = p.id " +
                            "WHERE r.user_id = ? ORDER BY r.created_at DESC")
                    .setParameter(1, user.getId())
                    .getResultList();

            StringBuilder arr = new StringBuilder("[");
            for (int i = 0; i < rows.size(); i++) {
                Object[] row = rows.get(i);
                if (i > 0)
                    arr.append(",");
                int pid = ((Number) row[4]).intValue();
                String primaryImg = getPrimaryImage(pid);
                arr.append("{")
                        .append("\"id\":").append(row[0]).append(",")
                        .append("\"rating\":").append(row[1]).append(",")
                        .append("\"comment\":\"").append(esc(row[2] != null ? row[2].toString() : "")).append("\",")
                        .append("\"date\":\"").append(row[3] != null ? row[3].toString().substring(0, 10) : "")
                        .append("\",")
                        .append("\"productId\":").append(pid).append(",")
                        .append("\"productName\":\"").append(esc(row[5] != null ? row[5].toString() : "")).append("\",")
                        .append("\"metalColor\":\"").append(esc(row[6] != null ? row[6].toString() : "")).append("\",")
                        .append("\"sizeLength\":\"").append(esc(row[7] != null ? row[7].toString() : "")).append("\",")
                        .append("\"imageUrl\":\"").append(esc(primaryImg)).append("\"")
                        .append("}");
            }
            arr.append("]");
            return ResponseEntity.ok("{\"success\":true,\"reviews\":" + arr + "}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    private String getPrimaryImage(int productId) {
        List<String> urls = em.createQuery(
                "SELECT i.imageUrl FROM ProductImage i WHERE i.product.id = :pid ORDER BY i.isPrimary DESC, i.id ASC",
                String.class).setParameter("pid", productId).setMaxResults(1).getResultList();
        return urls.isEmpty() ? "" : urls.get(0);
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.auracraft.service.NotificationService notificationService;

    @org.springframework.beans.factory.annotation.Autowired
    private com.auracraft.service.AuditLogService auditLogService;

    // ── POST /api/reviews ────────────────────────────────────────────────────
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> addReview(@RequestBody JsonNode body, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        User user = (session != null) ? (User) session.getAttribute("loggedInUser") : null;
        if (user == null)
            return ResponseEntity.status(401).body("{\"success\":false,\"message\":\"Not authenticated\"}");

        try {
            int variantId = body.path("variantId").asInt(0);
            int productId = body.path("productId").asInt(0);
            int rating = body.path("rating").asInt(0);
            String comment = body.path("comment").asText("").trim();

            if (variantId <= 0 && productId > 0) {
                List<Object> vids = em
                        .createNativeQuery(
                                "SELECT id FROM product_variants WHERE product_id = ? ORDER BY id ASC LIMIT 1")
                        .setParameter(1, productId)
                        .getResultList();
                if (!vids.isEmpty()) {
                    variantId = ((Number) vids.get(0)).intValue();
                }
            }

            if (variantId <= 0) {
                return ResponseEntity.badRequest()
                        .body("{\"success\":false,\"message\":\"Product variant is required.\"}");
            }

            com.auracraft.entity.ProductVariant pv = em.find(com.auracraft.entity.ProductVariant.class,
                    variantId);
            if (pv == null) {
                return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"Invalid product variant.\"}");
            }

            if (rating < 1 || rating > 5) {
                rating = 5; // Default to 5 stars
            }

            com.auracraft.entity.Review review = new com.auracraft.entity.Review();
            review.setUser(user);
            review.setProductVariant(pv);
            review.setRating(rating);
            review.setComment(comment.isEmpty() ? null : comment);
            em.persist(review);

            if (notificationService != null) {
                notificationService.notifyStaffByRole(
                        List.of("ADMIN", "MANAGER", "SUPPORT_OFFICER"),
                        "NEW_REVIEW",
                        "New " + rating + "-Star Review by " + user.getFullName() + " for " + pv.getProduct().getName(),
                        "/admin.html");
            }

            auditLogService.log(request, "SUBMIT_REVIEW", "PRODUCT",
                    "Customer " + user.getEmail() + " posted a " + rating + "-star review for " + pv.getProduct().getName(),
                    "SUCCESS");

            return ResponseEntity.ok("{\"success\":true,\"message\":\"Review submitted successfully!\"}");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── DELETE /api/reviews/{id} ─────────────────────────────────────────────
    @DeleteMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> deleteReview(@PathVariable int id, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        User user = (session != null) ? (User) session.getAttribute("loggedInUser") : null;
        if (user == null)
            return ResponseEntity.status(401).body("{\"success\":false,\"message\":\"Not authenticated\"}");

        try {
            int deleted = em.createNativeQuery(
                    "DELETE FROM reviews WHERE id = ? AND (user_id = ? OR ? IN (SELECT id FROM users WHERE role IN ('ADMIN','STAFF')))")
                    .setParameter(1, id)
                    .setParameter(2, user.getId())
                    .setParameter(3, user.getId())
                    .executeUpdate();
            if (deleted == 0)
                return ResponseEntity.status(403)
                        .body("{\"success\":false,\"message\":\"Review not found or unauthorized.\"}");

            auditLogService.log(request, "DELETE_REVIEW", "PRODUCT",
                    "Review #" + id + " deleted by " + user.getEmail(),
                    "SUCCESS");

            return ResponseEntity.ok("{\"success\":true,\"message\":\"Review deleted.\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    private String esc(String s) {
        if (s == null)
            return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
