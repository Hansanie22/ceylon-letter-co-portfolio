package com.ceylonletterco.controller;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.logging.Logger;

@RestController
@RequestMapping("/api/admin")
@Transactional(readOnly = true)
public class AdminDashboardController {

    private static final Logger LOG = Logger.getLogger(AdminDashboardController.class.getName());

    @PersistenceContext
    private EntityManager em;

    private long getSafeLong(Object obj) {
        if (obj == null) return 0L;
        if (obj instanceof Number) return ((Number) obj).longValue();
        return 0L;
    }

    @GetMapping("/dashboard-overview")
    public ResponseEntity<String> getMetrics(HttpServletRequest request) {
        if (!isAdminOrStaff(request)) return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");
        try {
            Object webRevRaw = em.createQuery("SELECT SUM(o.totalAmount) FROM Order o WHERE o.orderStatus NOT IN ('CANCELLED', 'RETURNED')").getSingleResult();
            Object posRevRaw = em.createQuery("SELECT SUM(po.totalAmount) FROM PosOrder po WHERE po.orderStatus NOT IN ('CANCELLED', 'RETURNED')").getSingleResult();
            long totalRevenue = getSafeLong(webRevRaw) + getSafeLong(posRevRaw);
            
            long webNewOrders = getSafeLong(em.createQuery("SELECT COUNT(o) FROM Order o WHERE o.orderStatus = 'PENDING'").getSingleResult());
            long posNewOrders = getSafeLong(em.createQuery("SELECT COUNT(po) FROM PosOrder po WHERE po.orderStatus = 'CONFIRMED'").getSingleResult());
            long newOrders = webNewOrders + posNewOrders;

            long activeUsers = getSafeLong(em.createQuery("SELECT COUNT(u) FROM User u WHERE u.emailVerified = true").getSingleResult());
            long returnRequests = getSafeLong(em.createQuery("SELECT COUNT(o) FROM Order o WHERE o.orderStatus IN ('RETURN_REQUESTED', 'RETURN_APPROVED')").getSingleResult());
            
            return ResponseEntity.ok(String.format("{\"success\":true,\"totalRevenue\":%d,\"newOrders\":%d,\"activeUsers\":%d,\"returnRequests\":%d}",
                    totalRevenue, newOrders, activeUsers, returnRequests));
        } catch (Exception e) {
            LOG.severe("Failed to load metrics: " + e.getMessage());
            return ResponseEntity.ok("{\"success\":true,\"totalRevenue\":0,\"newOrders\":0,\"activeUsers\":0,\"returnRequests\":0}");
        }
    }

    @GetMapping("/sidebar-info")
    public ResponseEntity<String> getSidebarStats(HttpServletRequest request) {
        if (!isAdminOrStaff(request)) return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");
        try {
            long webPending = getSafeLong(em.createQuery("SELECT COUNT(o) FROM Order o WHERE o.orderStatus = 'PENDING'").getSingleResult());
            long posPending = getSafeLong(em.createQuery("SELECT COUNT(po) FROM PosOrder po WHERE po.orderStatus = 'CONFIRMED'").getSingleResult());
            long pendingOrders = webPending + posPending;
            
            long returnRequests = getSafeLong(em.createQuery("SELECT COUNT(o) FROM Order o WHERE o.orderStatus IN ('RETURN_REQUESTED', 'RETURN_APPROVED')").getSingleResult());
            long lowStock = getSafeLong(em.createQuery("SELECT COUNT(i) FROM Inventory i WHERE i.quantityOnHand < i.lowStockThreshold").getSingleResult());
            long openTickets = getSafeLong(em.createQuery("SELECT COUNT(m) FROM SupportMessage m WHERE m.isRead = false AND m.senderType = 'CUSTOMER'").getSingleResult());
            
            return ResponseEntity.ok(String.format("{\"success\":true,\"pendingOrders\":%d,\"openTickets\":%d,\"returnRequests\":%d,\"lowStock\":%d}",
                    pendingOrders, openTickets, returnRequests, lowStock));
        } catch (Exception e) {
            LOG.severe("Failed to load sidebar stats: " + e.getMessage());
            return ResponseEntity.ok("{\"success\":true,\"pendingOrders\":0,\"openTickets\":0,\"returnRequests\":0,\"lowStock\":0}");
        }
    }


    private boolean isAdminOrStaff(HttpServletRequest request) {
        com.ceylonletterco.entity.User user = (com.ceylonletterco.entity.User) request.getSession().getAttribute("loggedInUser");
        return user != null && user.getRole() != null && !"CUSTOMER".equalsIgnoreCase(user.getRole());
    }
}
