package com.ceylonletterco.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.JsonNode;
import com.ceylonletterco.entity.SupportTicket;
import com.ceylonletterco.entity.SupportMessage;

@RestController
@RequestMapping("/api/admin/messages")
public class AdminMessageController {
    @Autowired
    private com.ceylonletterco.repository.SupportTicketRepository ticketRepo;

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private com.ceylonletterco.service.NotificationService notificationService;

    @Autowired
    private com.ceylonletterco.service.AuditLogService auditLogService;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<?> getMessages(HttpServletRequest request) {
        // Fetch real support tickets from database
        java.util.List<com.ceylonletterco.entity.SupportTicket> tickets = ticketRepo.findAllByOrderByCreatedAtDesc();
        
        StringBuilder arr = new StringBuilder("[");
        for (int i = 0; i < tickets.size(); i++) {
            if (i > 0) arr.append(",");
            com.ceylonletterco.entity.SupportTicket t = tickets.get(i);
            try {
                String customerName = "Guest";
                try {
                    if (t.getUser() != null && t.getUser().getFullName() != null) {
                        customerName = t.getUser().getFullName();
                    }
                } catch (Exception e) {
                    // Ignore EntityNotFoundException if user was deleted
                    customerName = "Deleted User";
                }
                String subject = t.getSubject() != null ? t.getSubject() : "No Subject";
                arr.append("{")
                   .append("\"id\":").append(t.getId()).append(",")
                   .append("\"customerName\":\"").append(customerName.replace("\"", "\\\"")).append("\",")
                   .append("\"subject\":\"").append(subject.replace("\"", "\\\"")).append("\",")
                   .append("\"status\":\"").append(t.getStatus()).append("\"")
                   .append("}");
            } catch (Exception e) {
                // Skip problematic tickets
                if (arr.length() > 1 && arr.charAt(arr.length() - 1) == ',') {
                    arr.setLength(arr.length() - 1); // Remove trailing comma if we added one
                }
            }
        }
        arr.append("]");
        
        return ResponseEntity.ok().body("{\"success\":true, \"tickets\": " + arr.toString() + "}");
    }

    @GetMapping(value = "/{id}", produces = "application/json")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getTicketMessages(@PathVariable int id, HttpServletRequest request) {
        SupportTicket ticket = em.find(SupportTicket.class, id);
        if (ticket == null) return ResponseEntity.status(404).body("{\"success\":false,\"message\":\"Ticket not found.\"}");

        java.util.List<SupportMessage> messages = em.createQuery("SELECT m FROM SupportMessage m WHERE m.supportTicket.id = :tid ORDER BY m.sentAt ASC", SupportMessage.class)
                .setParameter("tid", id)
                .getResultList();

        // Mark user messages as read
        for (SupportMessage m : messages) {
            if (!m.getIsRead() && "USER".equals(m.getSenderType())) {
                m.setIsRead(true);
                em.merge(m);
            }
        }
        
        StringBuilder arr = new StringBuilder("[");
        for (int i = 0; i < messages.size(); i++) {
            if (i > 0) arr.append(",");
            SupportMessage m = messages.get(i);
            arr.append("{")
               .append("\"id\":").append(m.getId()).append(",")
               .append("\"senderType\":\"").append(m.getSenderType()).append("\",")
               .append("\"messageText\":\"").append(m.getMessageText() != null ? m.getMessageText().replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "") : "").append("\",")
               .append("\"sentAt\":\"").append(m.getSentAt() != null ? m.getSentAt().toString() : "").append("\"")
               .append("}");
        }
        arr.append("]");
        return ResponseEntity.ok().body("{\"success\":true, \"messages\": " + arr.toString() + "}");
    }

    @PostMapping(value = "/{id}", produces = "application/json")
    @Transactional
    public ResponseEntity<?> replyToTicket(@PathVariable int id, @RequestBody JsonNode body, HttpServletRequest request) {
        SupportTicket ticket = em.find(SupportTicket.class, id);
        if (ticket == null) return ResponseEntity.status(404).body("{\"success\":false,\"message\":\"Ticket not found.\"}");

        String text = body.path("message").asText("").trim();
        if (text.isEmpty()) return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"Message is required.\"}");

        SupportMessage m = new SupportMessage();
        m.setSupportTicket(ticket);
        m.setSenderType("ADMIN");
        m.setMessageText(text);
        m.setIsRead(true);
        em.persist(m);

        // Update ticket status to OPEN if it was CLOSED, though usually reply means it's answered.
        ticket.setStatus("OPEN");
        em.merge(ticket);

        if (ticket.getUser() != null) {
            notificationService.notifyUser(ticket.getUser().getId(), "SUPPORT_REPLY", "New reply from Support on Ticket #" + ticket.getId(), "/account.html");
        }

        auditLogService.log(request, "REPLY_SUPPORT_TICKET", "SUPPORT",
                "Staff replied to Ticket #" + ticket.getId() + " (" + ticket.getSubject() + ")",
                "SUCCESS");

        return ResponseEntity.ok("{\"success\":true,\"message\":\"Reply sent.\"}");
    }

    @PutMapping(value = "/{id}", produces = "application/json")
    @Transactional
    public ResponseEntity<?> updateTicketStatus(@PathVariable int id, @RequestBody JsonNode body, HttpServletRequest request) {
        SupportTicket ticket = em.find(SupportTicket.class, id);
        if (ticket == null) return ResponseEntity.status(404).body("{\"success\":false,\"message\":\"Ticket not found.\"}");

        String status = body.path("status").asText("").trim();
        if (!status.isEmpty()) {
            ticket.setStatus(status.toUpperCase());
            em.merge(ticket);

            auditLogService.log(request, "UPDATE_TICKET_STATUS", "SUPPORT",
                    "Support Ticket #" + ticket.getId() + " status changed to " + status.toUpperCase(),
                    "SUCCESS");
        }

        return ResponseEntity.ok("{\"success\":true,\"message\":\"Ticket updated.\"}");
    }
}
