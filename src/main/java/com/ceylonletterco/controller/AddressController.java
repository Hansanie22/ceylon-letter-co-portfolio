package com.auracraft.controller;

import com.auracraft.entity.Address;
import com.auracraft.entity.User;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AddressController – migrated from AddressServlet.
 * Handles /api/addresses/* endpoints.
 */
@RestController
@RequestMapping("/api/addresses")
@Transactional
public class AddressController {

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private Integer getUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return null;
        User user = (User) session.getAttribute("loggedInUser");
        return user != null ? user.getId() : null;
    }

    // ── GET /api/addresses ───────────────────────────────────────────────────
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<String> getAddresses(HttpServletRequest request) {
        Integer userId = getUserId(request);
        if (userId == null) return ResponseEntity.status(401).body("{\"success\":false,\"message\":\"Not authenticated\"}");

        try {
            List<Address> addresses = em.createQuery(
                    "SELECT a FROM Address a WHERE a.user.id = :uid ORDER BY a.isDefault DESC, a.id DESC",
                    Address.class).setParameter("uid", userId).getResultList();

            StringBuilder arr = new StringBuilder("[");
            for (int i = 0; i < addresses.size(); i++) {
                if (i > 0) arr.append(",");
                arr.append(addressToJson(addresses.get(i)));
            }
            arr.append("]");
            return ResponseEntity.ok("{\"success\":true,\"addresses\":" + arr + "}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── POST /api/addresses ──────────────────────────────────────────────────
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> addAddress(@RequestBody JsonNode body, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        User user = (session != null) ? (User) session.getAttribute("loggedInUser") : null;
        if (user == null) return ResponseEntity.status(401).body("{\"success\":false,\"message\":\"Not authenticated\"}");

        try {
            String label = body.path("addressLabel").asText("Home").trim();
            String line1 = body.path("addressLine1").asText("").trim();
            String line2 = body.path("addressLine2").asText("").trim();
            String city = body.path("city").asText("").trim();
            String postalCode = body.path("postalCode").asText("").trim();

            if (line1.isEmpty() || city.isEmpty()) {
                return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"Address line 1 and city are required.\"}");
            }

            Address address = new Address();
            // Use a managed reference for the User to avoid detached entity exceptions
            User managedUser = em.find(User.class, user.getId());
            if (managedUser == null) {
                return ResponseEntity.status(401).body("{\"success\":false,\"message\":\"User not found in database.\"}");
            }
            address.setUser(managedUser);
            address.setFullName(label);
            address.setStreet(line1);
            address.setDistrict(line2);
            address.setCity(city);
            address.setPostalCode(postalCode);
            address.setIsDefault(false);

            em.persist(address);
            return ResponseEntity.ok("{\"success\":true,\"message\":\"Address added successfully!\"}");
        } catch (Exception e) {
            java.io.StringWriter sw = new java.io.StringWriter();
            e.printStackTrace(new java.io.PrintWriter(sw));
            String stackTrace = sw.toString();
            return ResponseEntity.internalServerError().body("{\"success\":false,\"message\":\"" + esc(e.getMessage() + " | Trace: " + stackTrace) + "\"}");
        }
    }

    // ── PUT /api/addresses/{id} ──────────────────────────────────────────────
    @PutMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> updateAddress(@PathVariable int id, @RequestBody JsonNode body,
                                                 HttpServletRequest request) {
        Integer userId = getUserId(request);
        if (userId == null) return ResponseEntity.status(401).body("{\"success\":false,\"message\":\"Not authenticated\"}");

        try {
            Address address = em.find(Address.class, id);
            if (address == null || !address.getUser().getId().equals(userId)) {
                return ResponseEntity.status(404).body("{\"success\":false,\"message\":\"Address not found.\"}");
            }

            String label = body.path("addressLabel").asText(address.getFullName()).trim();
            String line1 = body.path("addressLine1").asText(address.getStreet()).trim();
            String line2 = body.path("addressLine2").asText(address.getDistrict()).trim();
            String city = body.path("city").asText(address.getCity()).trim();
            String postalCode = body.path("postalCode").asText(address.getPostalCode()).trim();

            address.setFullName(label);
            address.setStreet(line1);
            address.setDistrict(line2);
            address.setCity(city);
            address.setPostalCode(postalCode);

            em.merge(address);
            return ResponseEntity.ok("{\"success\":true,\"message\":\"Address updated successfully!\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── DELETE /api/addresses/{id} ───────────────────────────────────────────
    @DeleteMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> deleteAddress(@PathVariable int id, HttpServletRequest request) {
        Integer userId = getUserId(request);
        if (userId == null) return ResponseEntity.status(401).body("{\"success\":false,\"message\":\"Not authenticated\"}");

        try {
            Address address = em.find(Address.class, id);
            if (address == null || !address.getUser().getId().equals(userId)) {
                return ResponseEntity.status(404).body("{\"success\":false,\"message\":\"Address not found.\"}");
            }
            em.remove(address);
            return ResponseEntity.ok("{\"success\":true,\"message\":\"Address deleted.\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    private String addressToJson(Address a) {
        return "{\"id\":" + a.getId()
            + ",\"addressLabel\":\"" + esc(a.getFullName()) + "\""
            + ",\"addressLine1\":\"" + esc(a.getStreet()) + "\""
            + ",\"addressLine2\":\"" + esc(a.getDistrict()) + "\""
            + ",\"city\":\"" + esc(a.getCity()) + "\""
            + ",\"postalCode\":\"" + esc(a.getPostalCode()) + "\""
            + "}";
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
