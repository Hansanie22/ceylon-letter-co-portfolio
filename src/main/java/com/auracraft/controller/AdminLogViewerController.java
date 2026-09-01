package com.auracraft.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/admin/logs")
public class AdminLogViewerController {
    @GetMapping
    public ResponseEntity<?> getLogs(HttpServletRequest request) {
        java.util.List<java.util.Map<String, String>> logEntries = new java.util.ArrayList<>();
        try {
            java.io.File file = new java.io.File("spring.log");
            if (file.exists()) {
                java.util.List<String> lines = java.nio.file.Files.readAllLines(file.toPath());
                int start = Math.max(0, lines.size() - 100);
                for (int i = start; i < lines.size(); i++) {
                    String line = lines.get(i);
                    String level = line.contains("ERROR") ? "SEVERE" : (line.contains("WARN") ? "WARNING" : "INFO");
                    logEntries.add(java.util.Map.of(
                        "timestamp", "System Log",
                        "level", level,
                        "logger", "spring.log",
                        "message", line.replace("\"", "\\\"")
                    ));
                }
            }
        } catch (Exception e) {
            // ignore
        }

        StringBuilder arr = new StringBuilder("[");
        for (int i = logEntries.size() - 1; i >= 0; i--) {
            if (i < logEntries.size() - 1) arr.append(",");
            java.util.Map<String, String> log = logEntries.get(i);
            arr.append("{")
               .append("\"timestamp\":\"").append(log.get("timestamp")).append("\",")
               .append("\"level\":\"").append(log.get("level")).append("\",")
               .append("\"logger\":\"").append(log.get("logger")).append("\",")
               .append("\"message\":\"").append(log.get("message")).append("\"")
               .append("}");
        }
        arr.append("]");
        return ResponseEntity.ok().body("{\"success\":true, \"logs\": " + arr.toString() + "}");
    }
}
