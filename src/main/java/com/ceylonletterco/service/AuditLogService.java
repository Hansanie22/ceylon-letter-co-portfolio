package com.ceylonletterco.service;

import com.ceylonletterco.entity.AuditLog;
import com.ceylonletterco.entity.User;
import com.ceylonletterco.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AuditLogService {

    @Autowired
    private AuditLogRepository auditLogRepository;

    /**
     * Primary logging method using HttpServletRequest for context.
     */
    public void log(HttpServletRequest request, String action, String category, String details, String status) {
        try {
            String userEmail = "Guest";
            String userRole = "GUEST";

            if (request != null) {
                HttpSession session = request.getSession(false);
                if (session != null) {
                    User user = (User) session.getAttribute("loggedInUser");
                    if (user != null) {
                        userEmail = user.getEmail() != null ? user.getEmail() : (user.getFullName() != null ? user.getFullName() : "User#" + user.getId());
                        userRole = user.getRole() != null ? user.getRole() : "CUSTOMER";
                    } else if (session.getAttribute("userEmail") != null) {
                        userEmail = String.valueOf(session.getAttribute("userEmail"));
                        if (session.getAttribute("userRole") != null) {
                            userRole = String.valueOf(session.getAttribute("userRole"));
                        }
                    }
                }
            }

            String ip = extractClientIp(request);
            String ua = extractUserAgent(request);

            AuditLog log = new AuditLog(userEmail, userRole, action, category, details, ip, ua, status);
            auditLogRepository.save(log);
        } catch (Exception e) {
            System.err.println("⚠️ AuditLogService failed to save log: " + e.getMessage());
        }
    }

    /**
     * Overloaded method with explicit user info.
     */
    public void log(String userEmail, String userRole, String action, String category, String details, String ip, String userAgent, String status) {
        try {
            AuditLog log = new AuditLog(userEmail, userRole, action, category, details, ip, userAgent, status);
            auditLogRepository.save(log);
        } catch (Exception e) {
            System.err.println("⚠️ AuditLogService failed to save log: " + e.getMessage());
        }
    }

    /**
     * Fetch logs with filters.
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> getLogs(String category, String status, String startDate, String endDate, String search, int page, int size) {
        LocalDateTime start = null;
        LocalDateTime end = null;

        if (startDate != null && !startDate.isBlank()) {
            start = LocalDate.parse(startDate).atStartOfDay();
        }
        if (endDate != null && !endDate.isBlank()) {
            end = LocalDate.parse(endDate).atTime(LocalTime.MAX);
        }

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return auditLogRepository.findWithFilters(
                (category != null && !category.isBlank() && !"ALL".equalsIgnoreCase(category)) ? category.toUpperCase() : null,
                (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) ? status.toUpperCase() : null,
                start, end,
                (search != null && !search.isBlank()) ? search.trim() : null,
                pageRequest
        );
    }

    /**
     * Fetch all logs for CSV export.
     */
    @Transactional(readOnly = true)
    public List<AuditLog> getLogsForExport(String category, String status, String startDate, String endDate) {
        LocalDateTime start = null;
        LocalDateTime end = null;

        if (startDate != null && !startDate.isBlank()) {
            start = LocalDate.parse(startDate).atStartOfDay();
        }
        if (endDate != null && !endDate.isBlank()) {
            end = LocalDate.parse(endDate).atTime(LocalTime.MAX);
        }

        return auditLogRepository.findForExport(
                (category != null && !category.isBlank() && !"ALL".equalsIgnoreCase(category)) ? category.toUpperCase() : null,
                (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) ? status.toUpperCase() : null,
                start, end
        );
    }

    /**
     * Calculate KPI summary metrics.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSummary() {
        Map<String, Object> res = new HashMap<>();
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();

        long total = auditLogRepository.count();
        long todayCount = auditLogRepository.countSince(startOfToday);
        long authCount = auditLogRepository.countByCategory("AUTH");
        long orderPosCount = auditLogRepository.countByCategory("ORDER") + auditLogRepository.countByCategory("POS");

        res.put("totalLogs", total);
        res.put("todayLogs", todayCount);
        res.put("authLogs", authCount);
        res.put("orderPosLogs", orderPosCount);
        return res;
    }

    /**
     * Clear all logs or logs older than N days.
     */
    @Transactional
    public int clearLogs(int olderThanDays, HttpServletRequest request) {
        int deleted;
        if (olderThanDays <= 0) {
            deleted = (int) auditLogRepository.count();
            auditLogRepository.deleteAllInBatch();
        } else {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(olderThanDays);
            deleted = auditLogRepository.deleteOlderThan(cutoff);
        }

        // Log the clear action itself
        log(request, "CLEAR_AUDIT_LOGS", "SYSTEM",
            "Admin cleared " + deleted + " audit log entries" + (olderThanDays > 0 ? " (older than " + olderThanDays + " days)" : " (all logs)"),
            "SUCCESS");

        return deleted;
    }

    public static String extractClientIp(HttpServletRequest request) {
        if (request == null) return "127.0.0.1";
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "127.0.0.1";
    }

    public static String extractUserAgent(HttpServletRequest request) {
        if (request == null) return "Unknown";
        String ua = request.getHeader("User-Agent");
        if (ua == null || ua.isBlank()) return "Unknown";
        if (ua.length() > 250) return ua.substring(0, 247) + "...";
        return ua;
    }
}
