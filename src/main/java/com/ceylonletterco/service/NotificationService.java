package com.auracraft.service;

import com.auracraft.entity.Notification;
import com.auracraft.websocket.NotificationEndpoint;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

@Service
@Transactional
public class NotificationService {

    @PersistenceContext
    private EntityManager em;
    
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Send notifications exclusively to staff members with specific assigned roles.
     * Prevents administrative/staff alerts from ever leaking to regular customers.
     */
    @Async("taskExecutor")
    public void notifyStaffByRole(List<String> roles, String type, String message, String url) {
        try {
            if (roles == null || roles.isEmpty()) return;
            
            // Query only staff user IDs matching the designated roles
            List<Integer> staffIds = em.createQuery(
                "SELECT u.id FROM User u WHERE u.role IN :roles", Integer.class)
                .setParameter("roles", roles)
                .getResultList();

            for (Integer userId : staffIds) {
                Notification notification = new Notification(userId, type, message);
                em.persist(notification);
                
                ObjectNode payload = mapper.createObjectNode();
                payload.put("id", notification.getId());
                payload.put("type", type);
                payload.put("message", message);
                payload.put("url", url);
                payload.put("createdAt", notification.getCreatedAt() != null ? notification.getCreatedAt().toString() : java.time.LocalDateTime.now().toString());
                
                NotificationEndpoint.sendNotification(userId.toString(), payload.toString());
            }
        } catch (Exception e) {
            System.err.println("Failed to notify staff roles " + roles + ": " + e.getMessage());
        }
    }

    /**
     * Convenience method to notify Admins and Managers
     */
    @Async("taskExecutor")
    public void notifyAdmins(String type, String message, String url) {
        notifyStaffByRole(List.of("ADMIN", "MANAGER"), type, message, url);
    }

    /**
     * Broadcast public customer announcements (e.g. New Product Arrivals, Back in Stock) to CUSTOMER role users ONLY.
     */
    @Async("taskExecutor")
    public void broadcastToCustomers(String type, String message, String url) {
        try {
            List<Integer> customerIds = em.createQuery(
                "SELECT u.id FROM User u WHERE u.role = 'CUSTOMER'", Integer.class)
                .getResultList();
            
            for (Integer userId : customerIds) {
                Notification notification = new Notification(userId, type, message);
                em.persist(notification);
                
                ObjectNode payload = mapper.createObjectNode();
                payload.put("id", notification.getId());
                payload.put("type", type);
                payload.put("message", message);
                payload.put("url", url);
                payload.put("createdAt", notification.getCreatedAt() != null ? notification.getCreatedAt().toString() : java.time.LocalDateTime.now().toString());
                
                NotificationEndpoint.sendNotification(userId.toString(), payload.toString());
            }
        } catch (Exception e) {
            System.err.println("Failed to broadcast to customers: " + e.getMessage());
        }
    }

    /**
     * Broadcast alias for public updates (routes to customers only)
     */
    @Async("taskExecutor")
    public void broadcastToAllUsers(String type, String message, String url) {
        broadcastToCustomers(type, message, url);
    }

    /**
     * Notify a single specific user (Customer or Staff)
     */
    @Async("taskExecutor")
    public void notifyUser(Integer userId, String type, String message, String url) {
        try {
            if (userId == null) return;
            Notification notification = new Notification(userId, type, message);
            em.persist(notification);
            
            ObjectNode payload = mapper.createObjectNode();
            payload.put("id", notification.getId());
            payload.put("type", type);
            payload.put("message", message);
            payload.put("url", url);
            payload.put("createdAt", notification.getCreatedAt() != null ? notification.getCreatedAt().toString() : java.time.LocalDateTime.now().toString());
            
            NotificationEndpoint.sendNotification(userId.toString(), payload.toString());
        } catch (Exception e) {
            System.err.println("Failed to notify user " + userId + ": " + e.getMessage());
        }
    }
}
