package com.ceylonletterco.controller;

import com.ceylonletterco.entity.User;
import com.ceylonletterco.service.AuthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.ceylonletterco.service.CloudinaryService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;

/**
 * AuthController – migrated from AuthServlet.
 * Handles all /api/auth/* endpoints.
 * All API URL patterns are identical to the original.
 */
@RestController
@RequestMapping("/api/auth")
@Transactional
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private CloudinaryService cloudinaryService;

    @PersistenceContext
    private EntityManager em;

    private static final ObjectMapper mapper = new ObjectMapper();

    // ── POST /api/auth/upload-avatar ─────────────────────────────────────────
    @PostMapping(value = "/upload-avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> uploadAvatar(@RequestParam("avatar") MultipartFile file, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        User user = (session != null) ? (User) session.getAttribute("loggedInUser") : null;
        if (user == null) return ResponseEntity.status(401).body("{\"success\":false,\"message\":\"Not authenticated\"}");

        try {
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"No file uploaded.\"}");
            }

            String url = cloudinaryService.uploadImage(file);
            User managedUser = em.find(User.class, user.getId());
            managedUser.setProfileImageUrl(url);
            em.merge(managedUser);
            em.flush();

            // Update session
            session.setAttribute("loggedInUser", managedUser);

            return ResponseEntity.ok("{\"success\":true,\"url\":\"" + url + "\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── GET /api/auth/me ─────────────────────────────────────────────────────
    @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getMe(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        User user = (session != null) ? (User) session.getAttribute("loggedInUser") : null;

        if (user == null) {
            return ResponseEntity.ok("{\"success\":false,\"message\":\"Not authenticated\"}");
        }

        try {
            String memberSince = "";
            if (user.getCreatedAt() != null) {
                memberSince = user.getCreatedAt().format(DateTimeFormatter.ofPattern("MMMM yyyy"));
            }
            String initials = buildInitials(user.getFullName());
            String[] nameParts = splitName(user.getFullName());

            ObjectNode node = mapper.createObjectNode();
            node.put("success", true);
            node.put("id", user.getId());
            node.put("fullName", user.getFullName());
            node.put("firstName", nameParts[0]);
            node.put("lastName", nameParts[1]);
            if (user.getEmail() != null) node.put("email", user.getEmail()); else node.putNull("email");
            node.put("role", user.getRole());
            node.put("initials", initials);
            node.put("memberSince", memberSince);
            node.put("isSubscribed", user.isSubscribed());
            if (user.getProfileImageUrl() != null) node.put("avatarUrl", user.getProfileImageUrl()); else node.putNull("avatarUrl");
            if (user.getPhone() != null && !user.getPhone().isBlank()) node.put("phone", user.getPhone()); else node.putNull("phone");
            if (user.getDateOfBirth() != null) node.put("dob", user.getDateOfBirth().toString()); else node.putNull("dob");
            if (user.getProfileImageUrl() != null && !user.getProfileImageUrl().isBlank()) node.put("profileImageUrl", user.getProfileImageUrl()); else node.putNull("profileImageUrl");

            return ResponseEntity.ok(mapper.writeValueAsString(node));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"success\":false,\"message\":\"Server error\"}");
        }
    }

    @Autowired
    private com.ceylonletterco.service.AuditLogService auditLogService;

    // ── POST /api/auth/signup ────────────────────────────────────────────────
    @PostMapping(value = "/signup", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> signup(@RequestBody JsonNode body, HttpServletRequest request) {
        try {
            String name = body.path("name").asText("").trim();
            String email = body.path("email").asText("").trim();
            String password = body.path("password").asText("").trim();
            String returnUrl = body.path("returnUrl").asText("").trim();

            if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"All fields are required!\"}");
            }
            String appBaseUrl = getBaseUrl(request);
            authService.registerUser(name, email, password, appBaseUrl, returnUrl);

            auditLogService.log(email, "CUSTOMER", "SIGNUP", "AUTH",
                    "New customer registered: " + name + " (" + email + ")",
                    com.ceylonletterco.service.AuditLogService.extractClientIp(request),
                    com.ceylonletterco.service.AuditLogService.extractUserAgent(request),
                    "SUCCESS");

            return ResponseEntity.ok("{\"success\":true,\"requiresVerification\":true,\"message\":\"Account created! Please check your email to verify your account before signing in.\"}");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── POST /api/auth/login ─────────────────────────────────────────────────
    @PostMapping(value = "/login", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> login(@RequestBody JsonNode body, HttpServletRequest request) {
        String email = body.path("email").asText("").trim();
        try {
            String password = body.path("password").asText("").trim();
            if (email.isEmpty() || password.isEmpty()) {
                return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"Email and password are required!\"}");
            }
            User user = authService.authenticate(email, password);
            if (user != null) {
                HttpSession session = request.getSession(true);
                session.setAttribute("loggedInUser", user);
                session.setAttribute("userId", user.getId());
                session.setAttribute("userEmail", user.getEmail());
                session.setAttribute("userRole", user.getRole());

                auditLogService.log(user.getEmail(), user.getRole(), "USER_LOGIN", "AUTH",
                        "User logged in successfully: " + user.getEmail() + " [Role: " + user.getRole() + "]",
                        com.ceylonletterco.service.AuditLogService.extractClientIp(request),
                        com.ceylonletterco.service.AuditLogService.extractUserAgent(request),
                        "SUCCESS");

                ObjectNode resp = mapper.createObjectNode();
                resp.put("success", true);
                resp.put("message", "Login successful!");
                resp.put("id", user.getId());
                resp.put("name", user.getFullName());
                resp.put("email", user.getEmail());
                resp.put("role", user.getRole());
                resp.put("isSubscribed", user.isSubscribed());
                if (user.getPhone() != null && !user.getPhone().isBlank()) resp.put("phone", user.getPhone()); else resp.putNull("phone");
                return ResponseEntity.ok(mapper.writeValueAsString(resp));
            } else {
                auditLogService.log(email, "GUEST", "LOGIN_FAILED", "AUTH",
                        "Failed login attempt for email: " + email,
                        com.ceylonletterco.service.AuditLogService.extractClientIp(request),
                        com.ceylonletterco.service.AuditLogService.extractUserAgent(request),
                        "FAILED");

                return ResponseEntity.status(401).body("{\"success\":false,\"message\":\"Invalid email or password.\"}");
            }
        } catch (Exception e) {
            auditLogService.log(email, "GUEST", "LOGIN_BLOCKED", "AUTH",
                    "Login blocked for " + email + ": " + e.getMessage(),
                    com.ceylonletterco.service.AuditLogService.extractClientIp(request),
                    com.ceylonletterco.service.AuditLogService.extractUserAgent(request),
                    "WARNING");

            if ("ACCOUNT_DISABLED".equals(e.getMessage())) {
                return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Your account has been disabled by an administrator.\"}");
            }
            if ("EMAIL_NOT_VERIFIED".equals(e.getMessage())) {
                return ResponseEntity.status(403).body(
                    "{\"success\":false,\"emailNotVerified\":true,\"message\":\"Please verify your email address before signing in. Check your inbox for the verification link.\"}");
            }
            return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── POST /api/auth/logout ────────────────────────────────────────────────
    @PostMapping(value = "/logout", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> logout(HttpServletRequest request) {
        auditLogService.log(request, "USER_LOGOUT", "AUTH", "User logged out", "SUCCESS");
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        return ResponseEntity.ok("{\"success\":true,\"message\":\"Logged out successfully\"}");
    }


    // ── POST /api/auth/phone/check ───────────────────────────────────────────
    @PostMapping(value = "/phone/check", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> phoneCheck(@RequestBody JsonNode body) {
        try {
            String phone = body.path("phone").asText("").trim();
            boolean exists = authService.checkPhoneExists(phone);
            return ResponseEntity.ok("{\"success\":true,\"exists\":" + exists + "}");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── POST /api/auth/phone/register ────────────────────────────────────────
    @PostMapping(value = "/phone/register", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> phoneRegister(@RequestBody JsonNode body, HttpServletRequest request) {
        try {
            String phone = body.path("phone").asText("").trim();
            String fullName = body.path("fullName").asText("").trim();
            String pin = body.path("pin").asText("").trim();
            if (phone.isEmpty() || fullName.isEmpty() || pin.isEmpty()) throw new Exception("All fields are required!");
            User user = authService.registerPhoneUser(phone, fullName, pin);
            HttpSession session = request.getSession(true);
            session.setAttribute("loggedInUser", user);
            session.setAttribute("userId", user.getId());
            session.setAttribute("userEmail", user.getEmail());
            session.setAttribute("userRole", user.getRole());
            return ResponseEntity.ok("{\"success\":true,\"message\":\"Registered successfully!\",\"id\":" + user.getId() + ",\"name\":\"" + esc(user.getFullName()) + "\",\"role\":\"" + user.getRole() + "\"}");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── POST /api/auth/phone/login ───────────────────────────────────────────
    @PostMapping(value = "/phone/login", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> phoneLogin(@RequestBody JsonNode body, HttpServletRequest request) {
        try {
            String phone = body.path("phone").asText("").trim();
            String pin = body.path("pin").asText("").trim();
            if (phone.isEmpty() || pin.isEmpty()) throw new Exception("Phone and PIN are required!");
            User user = authService.loginPhoneUser(phone, pin);
            if (user != null) {
                HttpSession session = request.getSession(true);
                session.setAttribute("loggedInUser", user);
                session.setAttribute("userId", user.getId());
                session.setAttribute("userEmail", user.getEmail());
                session.setAttribute("userRole", user.getRole());
                return ResponseEntity.ok("{\"success\":true,\"message\":\"Login successful!\",\"id\":" + user.getId() + ",\"name\":\"" + esc(user.getFullName()) + "\",\"role\":\"" + user.getRole() + "\"}");
            } else {
                return ResponseEntity.status(401).body("{\"success\":false,\"message\":\"Invalid phone or PIN.\"}");
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── POST /api/auth/change-password ───────────────────────────────────────
    @PostMapping(value = "/change-password", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> changePassword(@RequestBody JsonNode body, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        User user = (session != null) ? (User) session.getAttribute("loggedInUser") : null;
        if (user == null) return ResponseEntity.status(401).body("{\"success\":false,\"message\":\"Not authenticated\"}");
        try {
            String currentPw = body.path("currentPassword").asText("");
            String newPw = body.path("newPassword").asText("");
            String confirmPw = body.path("confirmPassword").asText("");
            if (currentPw.isEmpty() || newPw.isEmpty()) return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"All password fields are required.\"}");
            if (newPw.length() < 8) return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"New password must be at least 8 characters.\"}");
            if (!newPw.equals(confirmPw)) return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"New passwords do not match.\"}");
            authService.changePassword(user.getId(), currentPw, newPw);
            return ResponseEntity.ok("{\"success\":true,\"message\":\"Password changed successfully!\"}");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── POST /api/auth/subscribe ─────────────────────────────────────────────
    @PostMapping(value = "/subscribe", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> subscribe(@RequestBody JsonNode body, HttpServletRequest request) {
        try {
            String email = body.path("email").asText("").trim();
            if (email.isEmpty()) throw new Exception("Email is required!");
            authService.subscribeUser(email);
            HttpSession session = request.getSession(false);
            if (session != null) {
                User loggedInUser = (User) session.getAttribute("loggedInUser");
                if (loggedInUser != null && loggedInUser.getEmail().equalsIgnoreCase(email)) {
                    loggedInUser.setSubscribed(true);
                    session.setAttribute("loggedInUser", loggedInUser);
                }
            }
            return ResponseEntity.ok("{\"success\":true,\"message\":\"Thank you for subscribing!\"}");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── POST /api/auth/otp/firebase-login ────────────────────────────────────
    @PostMapping(value = "/otp/firebase-login", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> firebaseLogin(@RequestBody JsonNode body, HttpServletRequest request) {
        try {
            String phone = body.path("phone").asText("").trim();
            String firebaseUid = body.path("firebaseUid").asText("").trim();
            if (phone.isEmpty() || firebaseUid.isEmpty()) throw new Exception("Phone and Firebase UID are required!");
            User user = authService.loginOrRegisterOtpUser(phone);
            if (user.getProviderId() == null || !user.getProviderId().equals(firebaseUid)) {
                user.setProviderId(firebaseUid);
                user.setAuthProvider("FIREBASE_OTP");
            }
            HttpSession session = request.getSession(true);
            session.setAttribute("loggedInUser", user);
            session.setAttribute("userId", user.getId());
            session.setAttribute("userEmail", user.getEmail());
            session.setAttribute("userRole", user.getRole());

            ObjectNode resp = mapper.createObjectNode();
            resp.put("success", true);
            resp.put("message", "Login successful!");
            resp.put("id", user.getId());
            resp.put("name", user.getFullName());
            resp.put("email", user.getEmail() != null ? user.getEmail() : "");
            resp.put("role", user.getRole());
            resp.put("isSubscribed", user.isSubscribed());
            if (user.getPhone() != null && !user.getPhone().isBlank()) resp.put("phone", user.getPhone()); else resp.putNull("phone");
            return ResponseEntity.ok(mapper.writeValueAsString(resp));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── PUT /api/auth/me (update profile) ────────────────────────────────────
    @PutMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> updateProfile(@RequestBody JsonNode body, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        User sessionUser = (session != null) ? (User) session.getAttribute("loggedInUser") : null;
        if (sessionUser == null) return ResponseEntity.status(401).body("{\"success\":false,\"message\":\"Not authenticated\"}");
        try {
            String fullName = body.path("fullName").asText("").trim();
            String phone = body.path("phone").asText("").trim();
            String dob = body.path("dob").asText("").trim();
            if (fullName.isEmpty()) return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"Full name is required.\"}");
            User updated = authService.updateProfile(sessionUser.getId(), fullName, phone.isEmpty() ? null : phone, dob.isEmpty() ? null : dob);
            session.setAttribute("loggedInUser", updated);
            String[] nameParts = splitName(updated.getFullName());
            return ResponseEntity.ok("{\"success\":true,\"message\":\"Profile updated successfully!\",\"fullName\":\"" + esc(updated.getFullName()) + "\",\"firstName\":\"" + esc(nameParts[0]) + "\",\"lastName\":\"" + esc(nameParts[1]) + "\",\"initials\":\"" + esc(buildInitials(updated.getFullName())) + "\"}");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private String getBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        String ctx = request.getContextPath();
        if (!host.contains("localhost") && !host.contains("127.0.0.1")) ctx = "";
        return scheme + "://" + host + (port != 80 && port != 443 ? ":" + port : "") + ctx;
    }

    private String buildInitials(String fullName) {
        if (fullName == null || fullName.isBlank()) return "?";
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, 1).toUpperCase();
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }

    private String[] splitName(String fullName) {
        if (fullName == null || fullName.isBlank()) return new String[]{"", ""};
        int spaceIdx = fullName.trim().indexOf(' ');
        if (spaceIdx == -1) return new String[]{fullName.trim(), ""};
        return new String[]{fullName.trim().substring(0, spaceIdx), fullName.trim().substring(spaceIdx + 1)};
    }

    /** Escape double-quotes and backslashes for inline JSON strings. */
    private String esc(String s) {
        if (s == null) return "Unknown error";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
