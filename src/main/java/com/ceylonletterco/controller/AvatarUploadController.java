package com.ceylonletterco.controller;

import com.ceylonletterco.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Autowired;
import com.ceylonletterco.service.CloudinaryService;

import java.util.Base64;
/**
 * AvatarUploadController – migrated from AvatarUploadServlet.
 * Handles /api/avatar/upload endpoint.
 * NOTE: In the original app Cloudinary was used for image storage.
 *       The CloudinaryService integration is preserved here.
 */
@RestController
@RequestMapping("/api/avatar")
@Transactional
public class AvatarUploadController {

    @Autowired
    private CloudinaryService cloudinaryService;

    @PersistenceContext
    private EntityManager em;

    // ── POST /api/avatar/upload ──────────────────────────────────────────────
    @PostMapping(value = "/upload", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> uploadAvatar(@RequestParam("file") MultipartFile file,
                                                HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        User user = (session != null) ? (User) session.getAttribute("loggedInUser") : null;
        if (user == null) return ResponseEntity.status(401).body("{\"success\":false,\"message\":\"Not authenticated\"}");

        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"No file provided.\"}");
            }
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"Only image files are allowed.\"}");
            }
            if (file.getSize() > 5 * 1024 * 1024) {
                return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"File size must be less than 5MB.\"}");
            }

            String imageUrl = cloudinaryService.uploadImage(file);

            // Update user in session
            user.setProfileImageUrl(imageUrl);
            session.setAttribute("loggedInUser", user);

            // Update DB
            int updated = em.createNativeQuery("UPDATE users SET profile_image_url = ? WHERE id = ?")
                    .setParameter(1, imageUrl)
                    .setParameter(2, user.getId())
                    .executeUpdate();

            if (updated == 0) return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"Database update failed.\"}");

            return ResponseEntity.ok("{\"success\":true,\"message\":\"Avatar uploaded.\",\"imageUrl\":\"" + imageUrl + "\"}");
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body("{\"success\":false,\"message\":\"Upload failed: " + esc(e.getMessage()) + "\"}");
        }
    }

    private String esc(String s) {
        if (s == null) return "Upload failed";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
