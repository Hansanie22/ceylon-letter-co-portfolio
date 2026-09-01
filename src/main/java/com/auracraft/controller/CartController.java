package com.auracraft.controller;

import com.auracraft.entity.CartItem;
import com.auracraft.entity.ProductVariant;
import com.auracraft.entity.User;
import com.auracraft.service.CartService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CartController – migrated from CartServlet.
 * Handles all /api/cart endpoints.
 */
@RestController
@RequestMapping("/api/cart")
public class CartController {

    @Autowired
    private CartService cartService;

    @PersistenceContext
    private EntityManager em;

    // ── GET /api/cart ────────────────────────────────────────────────────────
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getCart(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        User user = (session != null) ? (User) session.getAttribute("loggedInUser") : null;
        if (user == null) return ResponseEntity.status(401).body("{\"success\":false,\"message\":\"Not authenticated\"}");

        try {
            List<CartItem> items = cartService.getCartItems(user.getId());

            // Build inventory map (quantity per variant)
            List<Object[]> invRows = em.createQuery(
                    "SELECT i.productVariant.id, i.quantityOnHand FROM Inventory i WHERE i.productVariant.product IS NOT NULL",
                    Object[].class).getResultList();
            Map<Integer, Integer> invMap = new HashMap<>();
            for (Object[] row : invRows) {
                invMap.put((Integer) row[0], row[1] instanceof Integer ? (Integer) row[1] : ((Number) row[1]).intValue());
            }

            StringBuilder arr = new StringBuilder("[");
            for (int i = 0; i < items.size(); i++) {
                CartItem item = items.get(i);
                ProductVariant v = item.getProductVariant();
                String imgUrl = cartService.getPrimaryImageUrl(v.getProduct().getId());
                int stock = invMap.getOrDefault(v.getId(), 0);
                boolean hasCustom = (item.getEngravingText() != null && !item.getEngravingText().isBlank())
                        || (item.getCustomResize() != null && !item.getCustomResize().isBlank());
                boolean needsDeposit = Boolean.TRUE.equals(v.getProduct().getRequiresDeposit()) || hasCustom;
                if (i > 0) arr.append(",");
                arr.append("{")
                    .append("\"cartItemId\":").append(item.getId()).append(",")
                    .append("\"id\":").append(v.getProduct().getId()).append(",")
                    .append("\"variantId\":").append(v.getId()).append(",")
                    .append("\"name\":\"").append(esc(v.getProduct().getName())).append("\",")
                    .append("\"price\":").append(v.getPrice() != null ? v.getPrice() : BigDecimal.ZERO).append(",")
                    .append("\"quantity\":").append(item.getQuantity()).append(",")
                    .append("\"img\":\"").append(esc(imgUrl)).append("\",")
                    .append("\"color\":\"").append(esc(v.getMetalColor())).append("\",")
                    .append("\"size\":\"").append(esc(v.getSizeLength())).append("\",")
                    .append("\"stock\":").append(stock).append(",")
                    .append("\"engravingText\":\"").append(esc(item.getEngravingText())).append("\",")
                    .append("\"customResize\":\"").append(esc(item.getCustomResize())).append("\",")
                    .append("\"hasCustomization\":").append(hasCustom).append(",")
                    .append("\"requiresDeposit\":").append(needsDeposit)
                    .append("}");
            }
            arr.append("]");
            return ResponseEntity.ok("{\"success\":true,\"cart\":" + arr + "}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"success\":false,\"message\":\"Error loading cart: " + esc(e.getMessage()) + "\"}");
        }
    }

    // ── POST /api/cart ───────────────────────────────────────────────────────
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> updateCart(@RequestBody JsonNode body, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        User sessionUser = (session != null) ? (User) session.getAttribute("loggedInUser") : null;
        if (sessionUser == null) return ResponseEntity.status(401).body("{\"success\":false,\"message\":\"Not authenticated\"}");

        try {
            String action = body.path("action").asText("add");
            if ("sync".equals(action)) {
                JsonNode itemsArr = body.path("items");
                if (itemsArr.isArray()) {
                    for (JsonNode itemObj : itemsArr) {
                        int variantId = itemObj.path("variantId").asInt(0);
                        int productId = itemObj.path("id").asInt(0);
                        int qty = itemObj.path("quantity").asInt(1);
                        String eng = itemObj.path("engravingText").asText("");
                        String res = itemObj.path("customResize").asText("");
                        if (variantId <= 0 && productId > 0) variantId = cartService.resolveVariantId(productId);
                        if (variantId <= 0) continue;
                        cartService.addOrUpdateCartItem(sessionUser.getId(), variantId, qty, "sync", eng, res);
                    }
                }
                return ResponseEntity.ok("{\"success\":true,\"message\":\"Cart synchronized\"}");
            } else {
                int variantId = body.path("variantId").asInt(0);
                int productId = body.path("id").asInt(0);
                int quantity = body.path("quantity").asInt(1);
                String eng = body.path("engravingText").asText("");
                String res = body.path("customResize").asText("");
                if (variantId <= 0 && productId > 0) variantId = cartService.resolveVariantId(productId);
                if (variantId <= 0) return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"Variant ID required\"}");
                cartService.addOrUpdateCartItem(sessionUser.getId(), variantId, quantity, action, eng, res);
                return ResponseEntity.ok("{\"success\":true,\"message\":\"Cart updated\"}");
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── DELETE /api/cart ─────────────────────────────────────────────────────
    @DeleteMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> deleteFromCart(@RequestParam(required = false) String clear,
                                                  @RequestParam(required = false) String variantId,
                                                  @RequestParam(required = false) String id,
                                                  HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        User user = (session != null) ? (User) session.getAttribute("loggedInUser") : null;
        if (user == null) return ResponseEntity.status(401).body("{\"success\":false,\"message\":\"Not authenticated\"}");

        try {
            if ("true".equals(clear)) {
                cartService.clearCart(user.getId());
                return ResponseEntity.ok("{\"success\":true,\"message\":\"Cart cleared\"}");
            }
            int idToRemove = 0;
            if (variantId != null && !variantId.isEmpty()) idToRemove = Integer.parseInt(variantId);
            else if (id != null && !id.isEmpty()) idToRemove = Integer.parseInt(id);

            if (idToRemove > 0) {
                cartService.removeCartItemByProductOrVariant(user.getId(), idToRemove);
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
