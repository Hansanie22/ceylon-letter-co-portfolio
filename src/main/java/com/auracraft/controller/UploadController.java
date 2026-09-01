package com.auracraft.controller;

import com.auracraft.service.CloudinaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import com.auracraft.entity.User;

@RestController
@RequestMapping("/api/upload")
public class UploadController {

    @Autowired
    private CloudinaryService cloudinaryService;

    @PostMapping
    public ResponseEntity<?> uploadFile(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "slip", required = false) MultipartFile slip,
            HttpServletRequest request) {

        // Use whichever field was provided
        MultipartFile upload = (file != null) ? file : slip;

        if (upload == null || upload.isEmpty()) {
            return ResponseEntity.badRequest().body("{\"success\":false, \"message\": \"No file provided\"}");
        }

        try {
            String url = cloudinaryService.uploadImage(upload);
            return ResponseEntity.ok().body("{\"success\":true, \"url\": \"" + url + "\"}");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("{\"success\":false, \"message\": \"" + e.getMessage() + "\"}");
        }
    }
}
