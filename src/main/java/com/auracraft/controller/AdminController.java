package com.auracraft.controller;

import com.auracraft.entity.User;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * AdminController – migrated from AdminServlet.
 * Handles /api/admin/* endpoints.
 *
 * NOTE: Role-check is done both here AND by AdminFilter for defence-in-depth.
 */
@RestController
@RequestMapping("/api/admin")
@Transactional
public class AdminController {

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private com.auracraft.service.AuditLogService auditLogService;

    private boolean isAdmin(HttpServletRequest request, StringBuilder out) {
        HttpSession session = request.getSession(false);
        User u = (session != null) ? (User) session.getAttribute("loggedInUser") : null;
        if (u == null) { out.append("{\"success\":false,\"message\":\"Access denied. Please log in.\"}"); return false; }
        String role = u.getRole() != null ? u.getRole().toUpperCase() : "";
        if ("CUSTOMER".equals(role)) { out.append("{\"success\":false,\"message\":\"Access denied. Administrative privileges required.\"}"); return false; }
        return true;
    }

    // ── GET /api/admin/users ─────────────────────────────────────────────────
    @GetMapping(value = "/users", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<String> getUsers(HttpServletRequest request) {
        StringBuilder sb = new StringBuilder();
        HttpSession session = request.getSession(false);
        User caller = (session != null) ? (User) session.getAttribute("loggedInUser") : null;
        if (caller == null) return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");
        String r = caller.getRole() != null ? caller.getRole().toUpperCase() : "";
        if ("CUSTOMER".equals(r)) {
            return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Admin or Staff privileges required.\"}");
        }

        List<User> users = em.createQuery("SELECT u FROM User u ORDER BY u.id DESC", User.class).getResultList();
        StringBuilder arr = new StringBuilder("[");
        for (int i = 0; i < users.size(); i++) {
            User u = users.get(i);
            if (i > 0) arr.append(",");
            arr.append("{")
                .append("\"id\":").append(u.getId()).append(",")
                .append("\"email\":").append(u.getEmail() != null ? "\"" + esc(u.getEmail()) + "\"" : "null").append(",")
                .append("\"fullName\":").append(u.getFullName() != null ? "\"" + esc(u.getFullName()) + "\"" : "null").append(",")
                .append("\"phone\":").append(u.getPhone() != null ? "\"" + esc(u.getPhone()) + "\"" : "null").append(",")
                .append("\"role\":\"").append(esc(u.getRole())).append("\",")
                .append("\"isActive\":").append(u.isActive()).append(",")
                .append("\"emailVerified\":").append(u.isEmailVerified()).append(",")
                .append("\"createdAt\":\"").append(u.getCreatedAt() != null ? u.getCreatedAt().toString() : "").append("\"")
                .append("}");
        }
        arr.append("]");
        return ResponseEntity.ok("{\"success\":true,\"users\":" + arr + "}");
    }

    // ── PUT /api/admin/users/{id}/toggle-active ──────────────────────────────
    @PutMapping(value = "/users/{id}/toggle-active", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<String> toggleUserActiveStatus(@PathVariable int id, HttpServletRequest request) {
        StringBuilder sb = new StringBuilder();
        if (!isAdmin(request, sb)) return ResponseEntity.status(403).body(sb.toString());
        HttpSession session = request.getSession(false);
        User caller = (User) session.getAttribute("loggedInUser");
        if (!"ADMIN".equalsIgnoreCase(caller.getRole())) {
            return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Only Admins can change user status.\"}");
        }

        try {
            User user = em.find(User.class, id);
            if (user == null) {
                return ResponseEntity.status(404).body("{\"success\":false,\"message\":\"User not found.\"}");
            }
            
            if (caller.getId() == id && user.isActive()) {
                return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"You cannot deactivate your own account.\"}");
            }

            user.setActive(!user.isActive());
            em.merge(user);
            
            auditLogService.log(request, "TOGGLE_USER_STATUS", "SYSTEM",
                    (user.isActive() ? "Activated" : "Deactivated") + " account for " + user.getEmail() + " [ID: " + id + "]",
                    "SUCCESS");
            
            return ResponseEntity.ok("{\"success\":true,\"message\":\"User status updated successfully.\",\"isActive\":" + user.isActive() + "}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── PUT /api/admin/users/{id}/role ───────────────────────────────────────
    @PutMapping(value = "/users/{id}/role", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> updateUserRole(@PathVariable int id, @RequestBody JsonNode body,
                                                   HttpServletRequest request) {
        StringBuilder sb = new StringBuilder();
        if (!isAdmin(request, sb)) return ResponseEntity.status(403).body(sb.toString());
        HttpSession session = request.getSession(false);
        User caller = (User) session.getAttribute("loggedInUser");
        if (caller.getRole() == null || !caller.getRole().toUpperCase().contains("ADMIN")) {
            return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Super Administrator privileges required.\"}");
        }

        try {
            String newRole = body.path("role").asText("").trim().toUpperCase();
            // Allow comma-separated valid roles
            String[] parts = newRole.split(",");
            for (String part : parts) {
                String r = part.trim();
                if (!List.of("CUSTOMER", "ADMIN", "STAFF", "MANAGER", "STOCK_MANAGER", "SUPPORT_OFFICER", "SALES_REP", "PACKING").contains(r)) {
                    return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"Invalid role: " + r + "\"}");
                }
            }
            User user = em.find(User.class, id);
            if (user == null) return ResponseEntity.status(404).body("{\"success\":false,\"message\":\"User not found.\"}");
            user.setRole(newRole);
            em.merge(user);

            auditLogService.log(request, "UPDATE_USER_ROLE", "SYSTEM",
                    "Updated role for " + user.getEmail() + " [ID: " + id + "] to " + newRole,
                    "SUCCESS");

            return ResponseEntity.ok("{\"success\":true,\"message\":\"User role updated to " + newRole + ".\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── POST /api/admin/users ─────────────────────────────────────────────────
    @PostMapping(value = "/users", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createAdminUser(@RequestBody JsonNode body, HttpServletRequest request) {
        StringBuilder sb = new StringBuilder();
        if (!isAdmin(request, sb)) return ResponseEntity.status(403).body(sb.toString());
        HttpSession session = request.getSession(false);
        User caller = (User) session.getAttribute("loggedInUser");
        if (caller.getRole() == null || !caller.getRole().toUpperCase().contains("ADMIN")) {
            return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Super Administrator privileges required.\"}");
        }

        try {
            String email = body.path("email").asText("").trim();
            String fullName = body.path("fullName").asText("").trim();
            String password = body.path("password").asText("").trim();
            String role = body.path("role").asText("STAFF").trim().toUpperCase();

            if (email.isEmpty() || fullName.isEmpty() || password.isEmpty()) {
                return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"All fields are required.\"}");
            }

            List<User> existing = em.createQuery("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:e)", User.class)
                    .setParameter("e", email).getResultList();
            if (!existing.isEmpty()) return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"Email already in use.\"}");

            User newUser = new User();
            newUser.setEmail(email.toLowerCase());
            newUser.setFullName(fullName);
            newUser.setPassword(BCrypt.hashpw(password, BCrypt.gensalt(12)));
            newUser.setRole(role);
            newUser.setEmailVerified(true);
            em.persist(newUser);
            em.flush();

            auditLogService.log(request, "CREATE_STAFF_USER", "SYSTEM",
                    "Created staff account for " + newUser.getEmail() + " [Role: " + role + "]",
                    "SUCCESS");

            return ResponseEntity.ok("{\"success\":true,\"message\":\"User created.\",\"id\":" + newUser.getId() + "}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── DELETE /api/admin/users/{id} ──────────────────────────────────────────
    @DeleteMapping(value = "/users/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> deleteUser(@PathVariable int id, HttpServletRequest request) {
        StringBuilder sb = new StringBuilder();
        if (!isAdmin(request, sb)) return ResponseEntity.status(403).body(sb.toString());
        HttpSession session = request.getSession(false);
        User caller = (User) session.getAttribute("loggedInUser");
        if (caller.getRole() == null || !caller.getRole().toUpperCase().contains("ADMIN")) {
            return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Super Administrator privileges required.\"}");
        }
        if (caller.getId() == id) {
            return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"You cannot delete your own account.\"}");
        }
        try {
            User user = em.find(User.class, id);
            if (user == null) return ResponseEntity.status(404).body("{\"success\":false,\"message\":\"User not found.\"}");
            em.remove(user);

            auditLogService.log(request, "DELETE_USER", "SYSTEM",
                    "Deleted user account: " + user.getEmail() + " [ID: " + id + "]",
                    "SUCCESS");

            return ResponseEntity.ok("{\"success\":true,\"message\":\"User deleted.\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
