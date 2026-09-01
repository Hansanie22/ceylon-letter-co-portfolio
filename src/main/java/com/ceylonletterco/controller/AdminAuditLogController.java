package com.auracraft.controller;

import com.auracraft.entity.AuditLog;
import com.auracraft.entity.User;
import com.auracraft.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.PrintWriter;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/audit-logs")
public class AdminAuditLogController {

    @Autowired
    private AuditLogService auditLogService;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private boolean isAuthorized(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return false;
        User user = (User) session.getAttribute("loggedInUser");
        if (user == null) {
            String role = (String) session.getAttribute("userRole");
            return "ADMIN".equalsIgnoreCase(role) || "SUPER_ADMIN".equalsIgnoreCase(role) || "MANAGER".equalsIgnoreCase(role);
        }
        String role = user.getRole();
        return "ADMIN".equalsIgnoreCase(role) || "SUPER_ADMIN".equalsIgnoreCase(role) || "MANAGER".equalsIgnoreCase(role);
    }

    /**
     * GET /api/admin/audit-logs
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getLogs(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "30") int size,
            HttpServletRequest request) {

        if (!isAuthorized(request)) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "Unauthorized"));
        }

        try {
            Page<AuditLog> logPage = auditLogService.getLogs(category, status, startDate, endDate, search, page, size);
            
            List<Map<String, Object>> list = logPage.getContent().stream().map(log -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", log.getId());
                m.put("userEmail", log.getUserEmail());
                m.put("userRole", log.getUserRole());
                m.put("action", log.getAction());
                m.put("category", log.getCategory());
                m.put("details", log.getDetails());
                m.put("ipAddress", log.getIpAddress());
                m.put("userAgent", log.getUserAgent());
                m.put("status", log.getStatus());
                m.put("createdAt", log.getCreatedAt() != null ? log.getCreatedAt().format(FMT) : "");
                return m;
            }).toList();

            Map<String, Object> res = new HashMap<>();
            res.put("success", true);
            res.put("logs", list);
            res.put("totalElements", logPage.getTotalElements());
            res.put("totalPages", logPage.getTotalPages());
            res.put("currentPage", logPage.getNumber());
            res.put("pageSize", logPage.getSize());
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * GET /api/admin/audit-logs/summary
     */
    @GetMapping(value = "/summary", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getSummary(HttpServletRequest request) {
        if (!isAuthorized(request)) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "Unauthorized"));
        }
        try {
            Map<String, Object> summary = auditLogService.getSummary();
            summary.put("success", true);
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * DELETE /api/admin/audit-logs/clear
     */
    @DeleteMapping(value = "/clear", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> clearLogs(
            @RequestParam(value = "olderThanDays", defaultValue = "0") int olderThanDays,
            HttpServletRequest request) {

        if (!isAuthorized(request)) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "Unauthorized"));
        }

        try {
            int deleted = auditLogService.clearLogs(olderThanDays, request);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "deletedCount", deleted,
                    "message", "Successfully cleared " + deleted + " audit log entries."
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * GET /api/admin/audit-logs/export
     */
    @GetMapping("/export")
    public void exportCsv(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            HttpServletRequest request,
            HttpServletResponse response) {

        if (!isAuthorized(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        try {
            List<AuditLog> logs = auditLogService.getLogsForExport(category, status, startDate, endDate);

            String filename = "audit-logs-" + java.time.LocalDate.now() + ".csv";
            response.setContentType("text/csv; charset=UTF-8");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");

            PrintWriter writer = response.getWriter();
            writer.write("\uFEFF"); // UTF-8 BOM
            writer.println("ID,Timestamp,User Email,Role,Category,Action,Details,IP Address,Status");

            for (AuditLog l : logs) {
                writer.println(String.format("\"%d\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"",
                        l.getId(),
                        l.getCreatedAt() != null ? l.getCreatedAt().format(FMT) : "",
                        escapeCsv(l.getUserEmail()),
                        escapeCsv(l.getUserRole()),
                        escapeCsv(l.getCategory()),
                        escapeCsv(l.getAction()),
                        escapeCsv(l.getDetails()),
                        escapeCsv(l.getIpAddress()),
                        escapeCsv(l.getStatus())
                ));
            }
            writer.flush();
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private String escapeCsv(String val) {
        if (val == null) return "";
        return val.replace("\"", "\"\"");
    }
}
