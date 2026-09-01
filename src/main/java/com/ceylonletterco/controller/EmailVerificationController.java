package com.auracraft.controller;

import com.auracraft.entity.User;
import com.auracraft.service.AuthService;
import com.auracraft.service.EmailVerificationService;
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
 * EmailVerificationController – migrated from EmailVerificationServlet.
 * Handles /api/auth/verify and /api/auth/resend-verification endpoints.
 */
@RestController
@RequestMapping("/api/auth")
@Transactional
public class EmailVerificationController {

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private AuthService authService;

    @Autowired
    private EmailVerificationService emailVerificationService;

    // ── GET /api/auth/verify?token=xxx ──────────────────────────────────────
    @GetMapping(value = "/verify", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> verify(@RequestParam String token,
                                          @RequestParam(required = false) String returnUrl,
                                          HttpServletRequest request) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"Verification token is required.\"}");
        }
        try {
            List<User> users = em.createQuery(
                    "SELECT u FROM User u WHERE u.verificationToken = :token", User.class)
                    .setParameter("token", token.trim())
                    .getResultList();

            if (users.isEmpty()) {
                return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"Invalid or expired verification link.\"}");
            }
            User user = users.get(0);
            if (user.getVerificationTokenExpiry() != null
                    && java.time.LocalDateTime.now().isAfter(user.getVerificationTokenExpiry())) {
                return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"Verification link has expired. Please request a new one.\"}");
            }

            user.setEmailVerified(true);
            user.setVerificationToken(null);
            user.setVerificationTokenExpiry(null);
            em.merge(user);

            // Auto-login after verification
            HttpSession session = request.getSession(true);
            session.setAttribute("loggedInUser", user);
            session.setAttribute("userId", user.getId());
            session.setAttribute("userEmail", user.getEmail());
            session.setAttribute("userRole", user.getRole());

            String redirect = (returnUrl != null && !returnUrl.isBlank()) ? returnUrl : "account.html";
            
            String name = user.getFullName() != null ? user.getFullName() : "";
            String role = user.getRole() != null ? user.getRole() : "CUSTOMER";
            String emailStr = user.getEmail() != null ? user.getEmail() : "";
            Integer id = user.getId() != null ? user.getId() : 0;
            Boolean isSubscribed = user.isSubscribed();
            
            String html = """
                <!DOCTYPE html>
                <html><head><title>Email Verified</title></head>
                <body>
                <script>
                  try {
                      var userObj = {
                        id: %d,
                        name: '%s',
                        email: '%s',
                        role: '%s',
                        isSubscribed: %b
                      };
                      sessionStorage.setItem('AuraCraft Studio_user', JSON.stringify(userObj));
                      localStorage.setItem('AuraCraft Studio_user', JSON.stringify(userObj));
                  } catch(e) {
                      console.error(e);
                  }
                  setTimeout(function() {
                      window.location.href='%s';
                  }, 50);
                </script>
                </body></html>
                """.formatted(id, esc(name), esc(emailStr), esc(role), isSubscribed, esc(redirect));

            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("{\"success\":false,\"message\":\"Verification failed: " + esc(e.getMessage()) + "\"}");
        }
    }

    // ── POST /api/auth/resend-verification ───────────────────────────────────
    @PostMapping(value = "/resend-verification", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> resendVerification(@RequestBody(required = false) com.fasterxml.jackson.databind.JsonNode body,
                                                      HttpServletRequest request) {
        String email = null;
        if (body != null) email = body.path("email").asText("").trim();
        if (email == null || email.isEmpty()) {
            return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"Email is required.\"}");
        }
        try {
            List<User> users = em.createQuery(
                    "SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email)", User.class)
                    .setParameter("email", email)
                    .getResultList();
            if (users.isEmpty()) {
                return ResponseEntity.ok("{\"success\":true,\"message\":\"If that email exists, a verification link has been sent.\"}");
            }
            User user = users.get(0);
            if (user.isEmailVerified()) {
                return ResponseEntity.ok("{\"success\":true,\"message\":\"Your email is already verified. Please log in.\"}");
            }
            // Generate new token
            String token = java.util.UUID.randomUUID().toString().replace("-", "");
            user.setVerificationToken(token);
            user.setVerificationTokenExpiry(java.time.LocalDateTime.now().plusHours(24));
            em.merge(user);

            String baseUrl = getBaseUrl(request);
            // Actually send the verification email
            emailVerificationService.sendVerificationEmail(user, baseUrl, null);
            return ResponseEntity.ok("{\"success\":true,\"message\":\"Verification email sent. Please check your inbox.\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    private String getBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        String ctx = request.getContextPath();
        if (!host.contains("localhost") && !host.contains("127.0.0.1")) ctx = "";
        return scheme + "://" + host + (port != 80 && port != 443 ? ":" + port : "") + ctx;
    }

    private String esc(String s) {
        if (s == null) return "Unknown error";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
