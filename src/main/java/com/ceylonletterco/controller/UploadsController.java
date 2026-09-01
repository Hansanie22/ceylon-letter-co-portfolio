package com.ceylonletterco.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/uploads")
public class UploadsController {
    @GetMapping
    public ResponseEntity<?> getUploads(HttpServletRequest request) {
        return ResponseEntity.ok().body("{\"success\":true, \"uploads\": []}");
    }
}
