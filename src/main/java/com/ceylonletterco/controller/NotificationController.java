package com.auracraft.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import com.auracraft.entity.User;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    @Autowired
    private EntityManager em;

    @GetMapping
    public ResponseEntity<?> getNotifications(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("loggedInUser") == null) {
            return ResponseEntity.status(401).body("{\"success\":false}");
        }
        User user = (User) session.getAttribute("loggedInUser");
        java.util.List<com.auracraft.entity.Notification> notifs = em.createQuery("SELECT n FROM Notification n WHERE n.recipientId = :uid ORDER BY n.id DESC", com.auracraft.entity.Notification.class)
                .setParameter("uid", user.getId())
                .getResultList();
        
        StringBuilder arr = new StringBuilder("[");
        for (int i = 0; i < notifs.size(); i++) {
            if (i > 0) arr.append(",");
            com.auracraft.entity.Notification n = notifs.get(i);
            arr.append("{")
               .append("\"id\":").append(n.getId()).append(",")
               .append("\"type\":\"").append(n.getType() != null ? n.getType().replace("\"", "\\\"") : "").append("\",")
               .append("\"message\":\"").append(n.getMessage() != null ? n.getMessage().replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "") : "").append("\",")
               .append("\"isRead\":").append(n.isRead()).append(",")
               .append("\"createdAt\":\"").append(n.getCreatedAt()).append("\"")
               .append("}");
        }
        arr.append("]");
        return ResponseEntity.ok().body("{\"success\":true, \"notifications\": " + arr.toString() + "}");
    }

    @PutMapping
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> markAllAsRead(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("loggedInUser") == null) {
            return ResponseEntity.status(401).body("{\"success\":false}");
        }
        User user = (User) session.getAttribute("loggedInUser");
        em.createQuery("UPDATE Notification n SET n.isRead = true WHERE n.recipientId = :uid")
          .setParameter("uid", user.getId())
          .executeUpdate();
        return ResponseEntity.ok().body("{\"success\":true}");
    }

    @DeleteMapping
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> clearAll(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("loggedInUser") == null) {
            return ResponseEntity.status(401).body("{\"success\":false}");
        }
        User user = (User) session.getAttribute("loggedInUser");
        em.createQuery("DELETE FROM Notification n WHERE n.recipientId = :uid")
          .setParameter("uid", user.getId())
          .executeUpdate();
        return ResponseEntity.ok().body("{\"success\":true}");
    }
}
