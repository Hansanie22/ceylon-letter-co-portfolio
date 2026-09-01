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

import java.util.List;

/**
 * DiscountSubscriptionController – migrated from DiscountSubscriptionServlet.
 * Handles /api/discounts/* endpoints.
 */
@RestController
@RequestMapping("/api/discounts")
@Transactional
public class DiscountSubscriptionController {

    @PersistenceContext
    private EntityManager em;
    
    @org.springframework.beans.factory.annotation.Autowired
    private com.auracraft.service.EmailVerificationService emailService;

    // ── POST /api/discounts/subscribe ────────────────────────────────────────
    @PostMapping(value = "/subscribe", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> subscribe(@RequestBody JsonNode body, HttpServletRequest request) {
        String email = body.path("email").asText("").trim();
        if (email.isEmpty()) {
            return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"Email is required.\"}");
        }
        try {
            // Check if already subscribed
            List<Object[]> existing = em.createNativeQuery(
                    "SELECT id FROM discount_subscribers WHERE LOWER(email) = LOWER(?)")
                    .setParameter(1, email)
                    .getResultList();
            if (!existing.isEmpty()) {
                return ResponseEntity.ok("{\"success\":true,\"message\":\"You are already subscribed!\"}");
            }
            em.createNativeQuery("INSERT INTO discount_subscribers (email, subscribed_at) VALUES (?, NOW())")
                    .setParameter(1, email.toLowerCase())
                    .executeUpdate();
                    
            try {
                emailService.sendSubscriptionWelcome(email);
            } catch (Exception ignored) {}
            
            return ResponseEntity.ok("{\"success\":true,\"message\":\"You have been subscribed to exclusive discounts!\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("{\"success\":false,\"message\":\"Subscription failed: " + esc(e.getMessage()) + "\"}");
        }
    }

    // ── DELETE /api/discounts/unsubscribe ────────────────────────────────────
    @DeleteMapping(value = "/unsubscribe", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> unsubscribe(@RequestParam String email) {
        try {
            em.createNativeQuery("DELETE FROM discount_subscribers WHERE LOWER(email) = LOWER(?)")
                    .setParameter(1, email.trim())
                    .executeUpdate();
            return ResponseEntity.ok("{\"success\":true,\"message\":\"You have been unsubscribed.\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
