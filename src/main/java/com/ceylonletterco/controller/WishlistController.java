package com.auracraft.controller;

import com.auracraft.entity.ProductVariant;
import com.auracraft.entity.User;
import com.auracraft.entity.WishlistItem;
import com.auracraft.service.WishlistService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * WishlistController – migrated from WishlistServlet.
 * Handles all /api/wishlist endpoints.
 */
@RestController
@RequestMapping("/api/wishlist")
public class WishlistController {

    @Autowired
    private WishlistService wishlistService;

    // ── GET /api/wishlist ────────────────────────────────────────────────────
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getWishlist(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        User user = (session != null) ? (User) session.getAttribute("loggedInUser") : null;
        if (user == null) return ResponseEntity.status(401).body("{\"success\":false,\"message\":\"Not authenticated\"}");

        try {
            List<WishlistItem> items = wishlistService.getWishlistItems(user.getId());
            StringBuilder arr = new StringBuilder("[");
            for (int i = 0; i < items.size(); i++) {
                WishlistItem item = items.get(i);
                ProductVariant v = item.getProductVariant();
                String imgUrl = wishlistService.getPrimaryImageUrl(v.getProduct().getId());
                BigDecimal price = v.getPrice() != null ? v.getPrice() : BigDecimal.ZERO;
                if (i > 0) arr.append(",");
                arr.append("{")
                    .append("\"id\":").append(v.getProduct().getId()).append(",")
                    .append("\"variantId\":").append(v.getId()).append(",")
                    .append("\"name\":\"").append(esc(v.getProduct().getName())).append("\",")
                    .append("\"img\":\"").append(esc(imgUrl)).append("\",")
                    .append("\"price\":").append(price)
                    .append("}");
            }
            arr.append("]");
            return ResponseEntity.ok("{\"success\":true,\"wishlist\":" + arr + "}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"success\":false,\"message\":\"Error loading wishlist: " + esc(e.getMessage()) + "\"}");
        }
    }

    // ── POST /api/wishlist ───────────────────────────────────────────────────
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> updateWishlist(@RequestBody JsonNode body, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        User sessionUser = (session != null) ? (User) session.getAttribute("loggedInUser") : null;
        if (sessionUser == null) return ResponseEntity.status(401).body("{\"success\":false,\"message\":\"Not authenticated\"}");

        try {
            String action = body.path("action").asText("add");
            if ("sync".equals(action)) {
                JsonNode itemsArr = body.path("items");
                if (itemsArr.isArray()) {
                    for (JsonNode itemObj : itemsArr) {
                        int productId = itemObj.path("id").asInt(0);
                        if (productId <= 0) continue;
                        wishlistService.addWishlistItem(sessionUser.getId(), productId);
                    }
                }
                return ResponseEntity.ok("{\"success\":true,\"message\":\"Wishlist synchronized\"}");
            } else {
                int productId = body.path("id").asInt(0);
                if (productId <= 0) return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"Product ID required\"}");
                wishlistService.addWishlistItem(sessionUser.getId(), productId);
                return ResponseEntity.ok("{\"success\":true,\"message\":\"Added to wishlist\"}");
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── DELETE /api/wishlist ─────────────────────────────────────────────────
    @DeleteMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> deleteFromWishlist(@RequestParam(required = false) String clear,
                                                      @RequestParam(required = false) String id,
                                                      HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        User sessionUser = (session != null) ? (User) session.getAttribute("loggedInUser") : null;
        if (sessionUser == null) return ResponseEntity.status(401).body("{\"success\":false,\"message\":\"Not authenticated\"}");

        try {
            if ("true".equals(clear)) {
                wishlistService.clearWishlist(sessionUser.getId());
                return ResponseEntity.ok("{\"success\":true,\"message\":\"Wishlist cleared\"}");
            }
            int idToRemove = (id != null && !id.isEmpty()) ? Integer.parseInt(id) : 0;
            if (idToRemove > 0) {
                wishlistService.removeWishlistItem(sessionUser.getId(), idToRemove);
                return ResponseEntity.ok("{\"success\":true,\"message\":\"Item removed\"}");
            } else {
                return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"Invalid ID\"}");
            }
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"Invalid number format\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
