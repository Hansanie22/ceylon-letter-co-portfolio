package com.ceylonletterco.controller;

import com.ceylonletterco.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * PasswordResetController – migrated from PasswordResetServlet.
 * Handles /api/auth/forgot-password and /api/auth/reset-password endpoints.
 */
@RestController
@RequestMapping("/api/auth")
@Transactional
public class PasswordResetController {

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private JavaMailSender mailSender;

    @Value("${app.mail.noreply.from:ceylonletterco@gmail.com}")
    private String fromAddress;

    @Autowired
    private com.ceylonletterco.service.EmailVerificationService emailVerificationService;

    @Autowired
    private com.ceylonletterco.service.AuditLogService auditLogService;

    // ── POST /api/auth/forgot-password ───────────────────────────────────────
    @PostMapping(value = "/forgot-password", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> forgotPassword(@RequestBody com.fasterxml.jackson.databind.JsonNode body,
                                                   HttpServletRequest request) {
        String email = body.path("email").asText("").trim();
        if (email.isEmpty()) {
            return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"Email is required.\"}");
        }
        try {
            List<User> users = em.createQuery(
                    "SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email)", User.class)
                    .setParameter("email", email).getResultList();

            // Always return success to prevent email enumeration
            if (!users.isEmpty()) {
                User user = users.get(0);
                String token = UUID.randomUUID().toString().replace("-", "");
                user.setPasswordResetToken(token);
                user.setPasswordResetTokenExpiry(LocalDateTime.now().plusHours(1));
                em.merge(user);
                emailVerificationService.sendResetEmail(user, getBaseUrl(request), token);

                auditLogService.log(request, "FORGOT_PASSWORD_REQUEST", "AUTH",
                        "Password reset link requested for " + user.getEmail(),
                        "SUCCESS");
            }
            return ResponseEntity.ok("{\"success\":true,\"message\":\"If an account with that email exists, a password reset link has been sent.\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── POST /api/auth/reset-password ────────────────────────────────────────
    @PostMapping(value = "/reset-password", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> resetPassword(@RequestBody com.fasterxml.jackson.databind.JsonNode body,
                                                HttpServletRequest request) {
        String token = body.path("token").asText("").trim();
        String newPassword = body.path("newPassword").asText("").trim();
        if (token.isEmpty() || newPassword.isEmpty()) {
            return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"Token and new password are required.\"}");
        }
        if (newPassword.length() < 8) {
            return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"Password must be at least 8 characters.\"}");
        }
        try {
            List<User> users = em.createQuery(
                    "SELECT u FROM User u WHERE u.passwordResetToken = :token", User.class)
                    .setParameter("token", token).getResultList();
            if (users.isEmpty()) {
                return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"Invalid or expired reset link.\"}");
            }
            User user = users.get(0);
            if (user.getPasswordResetTokenExpiry() != null
                    && LocalDateTime.now().isAfter(user.getPasswordResetTokenExpiry())) {
                return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"Reset link has expired. Please request a new one.\"}");
            }
            user.setPassword(org.mindrot.jbcrypt.BCrypt.hashpw(newPassword, org.mindrot.jbcrypt.BCrypt.gensalt(12)));
            user.setPasswordResetToken(null);
            user.setPasswordResetTokenExpiry(null);
            em.merge(user);

            auditLogService.log(request, "RESET_PASSWORD_SUCCESS", "AUTH",
                    "Password successfully reset for " + user.getEmail(),
                    "SUCCESS");

            return ResponseEntity.ok("{\"success\":true,\"message\":\"Password reset successful! You can now log in.\"}");
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
