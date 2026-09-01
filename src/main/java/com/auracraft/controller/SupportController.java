package com.auracraft.controller;

import com.auracraft.entity.SupportMessage;
import com.auracraft.entity.SupportTicket;
import com.auracraft.entity.User;
import com.auracraft.websocket.NotificationEndpoint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/support")
@Transactional
public class SupportController {

    @PersistenceContext
    private EntityManager em;
    
    @org.springframework.beans.factory.annotation.Autowired
    private com.auracraft.service.NotificationService notificationService;

    @org.springframework.beans.factory.annotation.Autowired
    private com.auracraft.service.AuditLogService auditLogService;
    
    private final ObjectMapper mapper = new ObjectMapper();

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> getActiveTicket(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        User user = (session != null) ? (User) session.getAttribute("loggedInUser") : null;

        if (user == null) {
            return ResponseEntity.status(401).body(mapper.createObjectNode().put("success", false).put("message", "Not authenticated"));
        }

        List<SupportTicket> activeTickets = em.createQuery(
                "SELECT t FROM SupportTicket t WHERE t.user.id = :uid AND t.status IN ('OPEN', 'IN_PROGRESS') ORDER BY t.createdAt DESC", 
                SupportTicket.class)
                .setParameter("uid", user.getId())
                .setMaxResults(1)
                .getResultList();

        ObjectNode response = mapper.createObjectNode();
        response.put("success", true);

        if (!activeTickets.isEmpty()) {
            SupportTicket ticket = activeTickets.get(0);
            ObjectNode ticketNode = response.putObject("ticket");
            ticketNode.put("id", ticket.getId());
            ticketNode.put("subject", ticket.getSubject());
            ticketNode.put("status", ticket.getStatus());

            List<SupportMessage> msgs = em.createQuery(
                    "SELECT m FROM SupportMessage m WHERE m.supportTicket.id = :tid ORDER BY m.sentAt ASC", 
                    SupportMessage.class)
                    .setParameter("tid", ticket.getId())
                    .getResultList();

            ArrayNode messagesArray = response.putArray("messages");
            for (SupportMessage m : msgs) {
                ObjectNode mNode = messagesArray.addObject();
                mNode.put("id", m.getId());
                mNode.put("senderType", m.getSenderType());
                mNode.put("messageText", m.getMessageText());
                mNode.put("sentAt", m.getSentAt() != null ? m.getSentAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "");
                
                // Mark user messages as read when requested by admin, but here we just fetch.
            }
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> sendMessage(@RequestBody JsonNode body, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        User user = (session != null) ? (User) session.getAttribute("loggedInUser") : null;

        if (user == null) {
            return ResponseEntity.status(401).body("{\"success\":false,\"message\":\"Not authenticated\"}");
        }

        String subject = body.path("subject").asText(null);
        String messageText = body.path("message").asText("").trim();

        if (messageText.isEmpty()) {
            return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"Message content is required.\"}");
        }

        try {
            SupportTicket activeTicket = null;
            boolean isNewTicket = false;
            
            // Check if user already has an active ticket
            List<SupportTicket> activeTickets = em.createQuery(
                    "SELECT t FROM SupportTicket t WHERE t.user.id = :uid AND t.status IN ('OPEN', 'IN_PROGRESS') ORDER BY t.createdAt DESC", 
                    SupportTicket.class)
                    .setParameter("uid", user.getId())
                    .setMaxResults(1)
                    .getResultList();

            if (!activeTickets.isEmpty()) {
                activeTicket = activeTickets.get(0);
            } else {
                // Create new ticket
                isNewTicket = true;
                activeTicket = new SupportTicket();
                activeTicket.setUser(em.find(User.class, user.getId()));
                activeTicket.setSubject(subject != null && !subject.trim().isEmpty() ? subject.trim() : "General Inquiry");
                activeTicket.setStatus("OPEN");
                em.persist(activeTicket);
                em.flush();
            }

            // Create message
            SupportMessage supportMsg = new SupportMessage();
            supportMsg.setSupportTicket(activeTicket);
            supportMsg.setSenderType("USER");
            supportMsg.setMessageText(messageText);
            supportMsg.setIsRead(false);
            em.persist(supportMsg);
            
            // Notify Admin after transaction commits
            final String fUserName = user.getFullName();
            final int fTicketId = activeTicket.getId();
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            notificationService.notifyStaffByRole(java.util.List.of("ADMIN", "MANAGER", "SUPPORT_OFFICER"), "NEW_SUPPORT_MESSAGE", "New Message from " + fUserName + " - Ticket #" + fTicketId, "/admin.html");
                        } catch (Exception ignored) {}
                    }
                }
            );

            auditLogService.log(request, isNewTicket ? "CREATE_SUPPORT_TICKET" : "SEND_SUPPORT_MESSAGE", "SUPPORT",
                    "Customer " + user.getEmail() + " submitted message on Ticket #" + activeTicket.getId() + (isNewTicket ? " (Subject: " + activeTicket.getSubject() + ")" : ""),
                    "SUCCESS");

            return ResponseEntity.ok("{\"success\":true,\"message\":\"Your message has been sent. We will get back to you soon!\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("{\"success\":false,\"message\":\"Failed to send message: " + esc(e.getMessage()) + "\"}");
        }
    }

    // Keep the old POST /api/support/message just in case any old UI components call it
    @PostMapping(value = "/message", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> sendLegacyMessage(@RequestBody JsonNode body, HttpServletRequest request) {
        return sendMessage(body, request);
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
