package com.auracraft.controller;

import com.auracraft.entity.HeroSlide;
import com.auracraft.entity.User;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@RestController
@RequestMapping("/api/admin/hero-slides")
public class AdminHeroSlideController {
    @Autowired
    private com.auracraft.service.HeroSlideService heroSlideService;

    @Autowired
    private com.auracraft.service.CloudinaryService cloudinaryService;

    @Autowired
    private com.auracraft.service.AuditLogService auditLogService;

    private boolean isAdmin(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return false;
        User u = (User) session.getAttribute("loggedInUser");
        return u != null && u.getRole() != null && u.getRole().toUpperCase().contains("ADMIN");
    }

    @PostMapping
    public ResponseEntity<?> createSlide(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value = "altText", required = false) String altText,
            @RequestParam(value = "tag", required = false) String tag,
            @RequestParam(value = "heading", required = false) String heading,
            @RequestParam(value = "description", required = false) String description,
            HttpServletRequest request) {
        
        if (!isAdmin(request)) return ResponseEntity.status(403).body("{\"success\":false}");

        try {
            boolean isVideo = file.getContentType() != null && file.getContentType().startsWith("video");
            String resourceType = isVideo ? "video" : "image";
            String url = cloudinaryService.uploadFile(file.getBytes(), resourceType, file.getOriginalFilename());

            HeroSlide slide = heroSlideService.createSlide(url, resourceType, altText, tag, heading, description);

            auditLogService.log(request, "CREATE_HERO_SLIDE", "SETTINGS",
                    "Uploaded new hero banner slide: " + (heading != null ? heading : "Slide #" + slide.getId()),
                    "SUCCESS");

            return ResponseEntity.ok().body("{\"success\":true, \"id\": " + slide.getId() + "}");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"success\":false, \"message\":\"" + e.getMessage() + "\"}");
        }
    }

    @DeleteMapping
    public ResponseEntity<?> deleteSlide(@RequestParam("id") Long id, HttpServletRequest request) {
        if (!isAdmin(request)) return ResponseEntity.status(403).body("{\"success\":false}");
        try {
            heroSlideService.deleteSlide(id);

            auditLogService.log(request, "DELETE_HERO_SLIDE", "SETTINGS",
                    "Deleted hero banner slide #" + id,
                    "SUCCESS");

            return ResponseEntity.ok().body("{\"success\":true}");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"success\":false}");
        }
    }
}
