package com.ceylonletterco.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @GetMapping("/api/debug/log")
    public ResponseEntity<String> getLog() {
        try {
            java.nio.file.Path path = java.nio.file.Paths.get("/root/spring-boot.log");
            if (!java.nio.file.Files.exists(path)) {
                return ResponseEntity.ok("Log file not found");
            }
            String content = java.nio.file.Files.readString(path);
            return ResponseEntity.ok(content);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error reading log: " + e.getMessage());
        }
    }
}
