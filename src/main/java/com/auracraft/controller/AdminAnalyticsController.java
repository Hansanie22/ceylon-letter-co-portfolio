package com.auracraft.controller;

import com.auracraft.entity.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Logger;

@RestController
@RequestMapping("/api/admin")
@Transactional(readOnly = true)
public class AdminAnalyticsController {

    private static final Logger LOG = Logger.getLogger(AdminAnalyticsController.class.getName());

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private ObjectMapper mapper;

    private boolean isAdminOrStaff(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("loggedInUser") == null) return false;
        String role = (String) session.getAttribute("userRole");
        return role.contains("ADMIN") || role.contains("MANAGER") || role.contains("STOCK_MANAGER") || role.contains("SUPPORT_OFFICER");
    }

    private long safeLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number) return ((Number) o).longValue();
        return 0L;
    }

    private double safeDouble(Object o) {
        if (o == null) return 0.0;
        if (o instanceof Number) return ((Number) o).doubleValue();
        return 0.0;
    }

    // ── GET /api/admin/analytics?period=daily|monthly|weekly|lifetime&start=&end= ─
    @GetMapping(value = "/analytics", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getAnalytics(
            @RequestParam(defaultValue = "daily") String period,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            HttpServletRequest request) {

        if (!isAdminOrStaff(request)) {
            return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");
        }

        try {
            ObjectNode resp = mapper.createObjectNode();
            resp.put("success", true);

            // ── Date range ─────────────────────────────────────────────────────
            LocalDateTime startDt;
            LocalDateTime endDt;

            if (start != null && !start.isBlank() && end != null && !end.isBlank()) {
                startDt = LocalDate.parse(start).atStartOfDay();
                endDt   = LocalDate.parse(end).atTime(23, 59, 59);
            } else if ("monthly".equals(period)) {
                startDt = LocalDateTime.now().withDayOfYear(1).withHour(0).withMinute(0).withSecond(0);
                endDt   = LocalDateTime.now();
            } else if ("weekly".equals(period)) {
                startDt = LocalDateTime.now().minusDays(6).withHour(0).withMinute(0).withSecond(0);
                endDt   = LocalDateTime.now();
            } else if ("daily".equals(period)) {
                startDt = LocalDateTime.now().minusDays(29).withHour(0).withMinute(0).withSecond(0);
                endDt   = LocalDateTime.now();
            } else {
                // Lifetime / all-time (default if period=lifetime or period=all)
                startDt = LocalDateTime.of(2020, 1, 1, 0, 0, 0);
                endDt   = LocalDateTime.now();
            }

            // ── 1. Summary KPI Cards (Strict Realized Revenue Accounting) ───
            // Realized Web Revenue: Only orders with verified payment (PAID) or successfully delivered COD
            Object webRevRaw = em.createQuery(
                    "SELECT SUM(o.totalAmount) FROM Order o " +
                    "WHERE o.orderStatus NOT IN ('CANCELLED', 'RETURNED') " +
                    "AND (o.paymentStatus = 'PAID' OR o.orderStatus = 'DELIVERED') " +
                    "AND o.createdAt BETWEEN :s AND :e")
                    .setParameter("s", startDt).setParameter("e", endDt).getSingleResult();
            double webRealizedRev = safeDouble(webRevRaw);

            // Realized POS Revenue: Delivered orders (full amount) + In-progress orders (advance paid or full paid)
            List<PosOrder> posActiveOrders = em.createQuery(
                    "SELECT po FROM PosOrder po WHERE po.orderStatus NOT IN ('CANCELLED', 'RETURNED') AND po.createdAt BETWEEN :s AND :e",
                    PosOrder.class).setParameter("s", startDt).setParameter("e", endDt).getResultList();

            double posRealizedRev = 0.0;
            for (PosOrder po : posActiveOrders) {
                String st = po.getOrderStatus() != null ? po.getOrderStatus() : "PENDING";
                String pm = po.getPaymentMethod() != null ? po.getPaymentMethod() : "FULL_PAID";
                double tot = po.getTotalAmount() != null ? po.getTotalAmount().doubleValue() : 0.0;
                double adv = po.getAdvancePaid() != null ? po.getAdvancePaid().doubleValue() : 0.0;

                if ("DELIVERED".equals(st) || "COMPLETED".equals(st)) {
                    posRealizedRev += tot;
                } else {
                    // In-progress: recognized realized cash is advancePaid (or total if FULL_PAID)
                    if ("FULL_PAID".equals(pm)) {
                        posRealizedRev += tot;
                    } else if (adv > 0) {
                        posRealizedRev += adv;
                    }
                }
            }

            double totalRevenue = webRealizedRev + posRealizedRev;

            long webOrders = safeLong(em.createQuery(
                    "SELECT COUNT(o) FROM Order o WHERE o.createdAt BETWEEN :s AND :e")
                    .setParameter("s", startDt).setParameter("e", endDt).getSingleResult());
            long posOrders = safeLong(em.createQuery(
                    "SELECT COUNT(po) FROM PosOrder po WHERE po.createdAt BETWEEN :s AND :e")
                    .setParameter("s", startDt).setParameter("e", endDt).getSingleResult());
            
            long totalOrders = webOrders + posOrders;

            long totalCustomers = safeLong(em.createQuery(
                    "SELECT COUNT(u) FROM User u WHERE u.role='CUSTOMER' AND u.createdAt BETWEEN :s AND :e")
                    .setParameter("s", startDt).setParameter("e", endDt).getSingleResult());

            double avgOrderValue = totalOrders > 0 ? totalRevenue / totalOrders : 0.0;

            long webPending = safeLong(em.createQuery(
                    "SELECT COUNT(o) FROM Order o WHERE o.orderStatus='PENDING' AND o.createdAt BETWEEN :s AND :e")
                    .setParameter("s", startDt).setParameter("e", endDt).getSingleResult());
            long posPending = safeLong(em.createQuery(
                    "SELECT COUNT(po) FROM PosOrder po WHERE po.orderStatus='PENDING' AND po.createdAt BETWEEN :s AND :e")
                    .setParameter("s", startDt).setParameter("e", endDt).getSingleResult());

            long pendingOrders = webPending + posPending;

            long webReturned = safeLong(em.createQuery(
                    "SELECT COUNT(o) FROM Order o WHERE o.orderStatus='RETURNED' AND o.createdAt BETWEEN :s AND :e")
                    .setParameter("s", startDt).setParameter("e", endDt).getSingleResult());
            long posReturned = safeLong(em.createQuery(
                    "SELECT COUNT(po) FROM PosOrder po WHERE po.orderStatus='RETURNED' AND po.createdAt BETWEEN :s AND :e")
                    .setParameter("s", startDt).setParameter("e", endDt).getSingleResult());

            long returnedOrders = webReturned + posReturned;

            // COGS & Loss
            Object webCogsRaw = em.createQuery(
                    "SELECT SUM(o.totalCost) FROM Order o WHERE o.orderStatus NOT IN ('CANCELLED', 'RETURNED') AND o.createdAt BETWEEN :s AND :e")
                    .setParameter("s", startDt).setParameter("e", endDt).getSingleResult();
            Object posCogsRaw = em.createQuery(
                    "SELECT SUM(po.totalCost) FROM PosOrder po WHERE po.orderStatus NOT IN ('CANCELLED', 'RETURNED') AND po.createdAt BETWEEN :s AND :e")
                    .setParameter("s", startDt).setParameter("e", endDt).getSingleResult();
            double totalCogs = safeDouble(webCogsRaw) + safeDouble(posCogsRaw);

            Object webLossRaw = em.createQuery(
                    "SELECT SUM(o.returnLoss) FROM Order o WHERE o.orderStatus='RETURNED' AND o.createdAt BETWEEN :s AND :e")
                    .setParameter("s", startDt).setParameter("e", endDt).getSingleResult();
            Object posLossRaw = em.createQuery(
                    "SELECT SUM(po.returnLoss) FROM PosOrder po WHERE po.orderStatus='RETURNED' AND po.createdAt BETWEEN :s AND :e")
                    .setParameter("s", startDt).setParameter("e", endDt).getSingleResult();
            double totalLoss = safeDouble(webLossRaw) + safeDouble(posLossRaw);

            double netProfit = totalRevenue - totalCogs - totalLoss;

            ObjectNode summary = resp.putObject("summary");
            summary.put("totalRevenue", Math.round(totalRevenue * 100.0) / 100.0);
            summary.put("totalOrders", totalOrders);
            summary.put("totalCustomers", totalCustomers);
            summary.put("avgOrderValue", Math.round(avgOrderValue * 100.0) / 100.0);
            summary.put("pendingOrders", pendingOrders);
            summary.put("returnedOrders", returnedOrders);
            summary.put("totalCogs", Math.round(totalCogs * 100.0) / 100.0);
            summary.put("totalLoss", Math.round(totalLoss * 100.0) / 100.0);
            summary.put("netProfit", Math.round(netProfit * 100.0) / 100.0);

            // ── 2. Revenue Over Time Chart (Realized Inflow Web + POS) ─────────
            DateTimeFormatter labelFmt = "monthly".equals(period) ? DateTimeFormatter.ofPattern("yyyy-MM") : DateTimeFormatter.ofPattern("yyyy-MM-dd");
            
            List<Order> webRevOrders = em.createQuery(
                    "SELECT o FROM Order o WHERE o.orderStatus NOT IN ('CANCELLED', 'RETURNED') " +
                    "AND (o.paymentStatus = 'PAID' OR o.orderStatus = 'DELIVERED') " +
                    "AND o.createdAt BETWEEN :s AND :e", Order.class)
                    .setParameter("s", startDt).setParameter("e", endDt).getResultList();

            java.util.Map<String, Double> webRevMap = new java.util.TreeMap<>();
            java.util.Map<String, Double> posRevMap = new java.util.TreeMap<>();
            java.util.Set<String> allLabels = new java.util.TreeSet<>();

            for (Order o : webRevOrders) {
                if (o.getCreatedAt() != null) {
                    String dtStr = o.getCreatedAt().format(labelFmt);
                    double amt = o.getTotalAmount() != null ? o.getTotalAmount().doubleValue() : 0.0;
                    webRevMap.put(dtStr, webRevMap.getOrDefault(dtStr, 0.0) + amt);
                    allLabels.add(dtStr);
                }
            }

            for (PosOrder po : posActiveOrders) {
                if (po.getCreatedAt() != null) {
                    String dtStr = po.getCreatedAt().format(labelFmt);
                    String st = po.getOrderStatus() != null ? po.getOrderStatus() : "PENDING";
                    String pm = po.getPaymentMethod() != null ? po.getPaymentMethod() : "FULL_PAID";
                    double tot = po.getTotalAmount() != null ? po.getTotalAmount().doubleValue() : 0.0;
                    double adv = po.getAdvancePaid() != null ? po.getAdvancePaid().doubleValue() : 0.0;
                    double amt = 0.0;

                    if ("DELIVERED".equals(st) || "COMPLETED".equals(st)) {
                        amt = tot;
                    } else if ("FULL_PAID".equals(pm)) {
                        amt = tot;
                    } else if (adv > 0) {
                        amt = adv;
                    }

                    if (amt > 0) {
                        posRevMap.put(dtStr, posRevMap.getOrDefault(dtStr, 0.0) + amt);
                        allLabels.add(dtStr);
                    }
                }
            }

            // If allLabels is empty, add today's date so chart initializes gracefully
            if (allLabels.isEmpty()) {
                allLabels.add(LocalDate.now().format(labelFmt));
            }

            ObjectNode revenueChart = resp.putObject("revenueChart");
            revenueChart.put("type", "monthly".equals(period) ? "monthly" : "daily");
            ArrayNode revLabels = revenueChart.putArray("labels");
            ArrayNode revValues = revenueChart.putArray("values"); // Total Revenue Array
            ArrayNode webValues = revenueChart.putArray("webValues");
            ArrayNode posValues = revenueChart.putArray("posValues");
            for (String label : allLabels) {
                revLabels.add(label);
                double w = Math.round(webRevMap.getOrDefault(label, 0.0) * 100.0) / 100.0;
                double p = Math.round(posRevMap.getOrDefault(label, 0.0) * 100.0) / 100.0;
                webValues.add(w);
                posValues.add(p);
                revValues.add(Math.round((w + p) * 100.0) / 100.0);
            }

            // ── 3. Orders by Status (Web + POS) ────────────────────────────────
            List<Object[]> webStatusData = em.createQuery(
                    "SELECT o.orderStatus, COUNT(o) FROM Order o WHERE o.createdAt BETWEEN :s AND :e GROUP BY o.orderStatus",
                    Object[].class).setParameter("s", startDt).setParameter("e", endDt).getResultList();
            List<Object[]> posStatusData = em.createQuery(
                    "SELECT po.orderStatus, COUNT(po) FROM PosOrder po WHERE po.createdAt BETWEEN :s AND :e GROUP BY po.orderStatus",
                    Object[].class).setParameter("s", startDt).setParameter("e", endDt).getResultList();

            java.util.Map<String, Long> statusMap = new java.util.HashMap<>();
            for (Object[] row : webStatusData) {
                String st = row[0] != null ? row[0].toString() : "UNKNOWN";
                statusMap.put(st, statusMap.getOrDefault(st, 0L) + safeLong(row[1]));
            }
            for (Object[] row : posStatusData) {
                String st = row[0] != null ? row[0].toString() : "UNKNOWN";
                statusMap.put(st, statusMap.getOrDefault(st, 0L) + safeLong(row[1]));
            }

            ObjectNode orderStatus = resp.putObject("orderStatus");
            ArrayNode statusLabels = orderStatus.putArray("labels");
            ArrayNode statusValues = orderStatus.putArray("values");
            for (java.util.Map.Entry<String, Long> entry : statusMap.entrySet()) {
                statusLabels.add(entry.getKey());
                statusValues.add(entry.getValue());
            }

            // ── 4. Revenue by Payment Method (Web + POS Realized) ──────────────
            java.util.Map<String, Double> methodMap = new java.util.HashMap<>();

            // Web payments
            List<Payment> webPayments = em.createQuery(
                    "SELECT p FROM Payment p WHERE p.order.orderStatus NOT IN ('CANCELLED', 'RETURNED') " +
                    "AND (p.paymentStatus = 'PAID' OR p.order.orderStatus = 'DELIVERED') " +
                    "AND p.createdAt BETWEEN :s AND :e", Payment.class)
                    .setParameter("s", startDt).setParameter("e", endDt).getResultList();
            for (Payment p : webPayments) {
                String m = p.getPaymentMethod() != null ? p.getPaymentMethod() : "CARD";
                double amt = p.getAmount() != null ? p.getAmount().doubleValue() : 0.0;
                methodMap.put(m, methodMap.getOrDefault(m, 0.0) + amt);
            }

            // POS payments
            for (PosOrder po : posActiveOrders) {
                String m = po.getPaymentMethod() != null ? po.getPaymentMethod() : "FULL_PAID";
                String st = po.getOrderStatus() != null ? po.getOrderStatus() : "PENDING";
                double tot = po.getTotalAmount() != null ? po.getTotalAmount().doubleValue() : 0.0;
                double adv = po.getAdvancePaid() != null ? po.getAdvancePaid().doubleValue() : 0.0;
                double amt = 0.0;
                if ("DELIVERED".equals(st) || "COMPLETED".equals(st)) amt = tot;
                else if ("FULL_PAID".equals(m)) amt = tot;
                else if (adv > 0) amt = adv;

                if (amt > 0) {
                    methodMap.put(m, methodMap.getOrDefault(m, 0.0) + amt);
                }
            }

            ObjectNode paymentMethod = resp.putObject("paymentMethod");
            ArrayNode pmLabels = paymentMethod.putArray("labels");
            ArrayNode pmValues = paymentMethod.putArray("values");
            for (java.util.Map.Entry<String, Double> entry : methodMap.entrySet()) {
                String method = entry.getKey();
                String label = switch (method) {
                    case "COD" -> "Cash on Delivery";
                    case "BANK_TRANSFER" -> "Bank Transfer";
                    case "PAYHERE", "CARD", "STRIPE" -> "Card Payment";
                    case "COD_WITH_DEPOSIT", "COD_WITH_BANK_DEPOSIT", "ADVANCE_COD" -> "Advance Paid + COD";
                    case "FULL_PAID" -> "Full Paid (POS/Slip)";
                    default -> method;
                };
                pmLabels.add(label);
                pmValues.add(Math.round(entry.getValue() * 100.0) / 100.0);
            }

            // ── 5. Top 5 Best-Selling Products (Web + POS) ────────────────────
            List<Object[]> webTopProducts = em.createQuery(
                    "SELECT oi.productVariant.product.name, SUM(oi.quantity) FROM OrderItem oi " +
                    "WHERE oi.order.createdAt BETWEEN :s AND :e AND oi.order.orderStatus NOT IN ('CANCELLED', 'RETURNED') " +
                    "GROUP BY oi.productVariant.product.name", Object[].class)
                    .setParameter("s", startDt).setParameter("e", endDt).getResultList();

            List<Object[]> posTopProducts = em.createQuery(
                    "SELECT poi.productVariant.product.name, SUM(poi.quantity) FROM PosOrderItem poi " +
                    "WHERE poi.posOrder.createdAt BETWEEN :s AND :e AND poi.posOrder.orderStatus NOT IN ('CANCELLED', 'RETURNED') " +
                    "GROUP BY poi.productVariant.product.name", Object[].class)
                    .setParameter("s", startDt).setParameter("e", endDt).getResultList();

            java.util.Map<String, Long> topProdMap = new java.util.HashMap<>();
            for (Object[] row : webTopProducts) {
                String pName = row[0] != null ? row[0].toString() : "Unknown";
                topProdMap.put(pName, topProdMap.getOrDefault(pName, 0L) + safeLong(row[1]));
            }
            for (Object[] row : posTopProducts) {
                String pName = row[0] != null ? row[0].toString() : "Unknown";
                topProdMap.put(pName, topProdMap.getOrDefault(pName, 0L) + safeLong(row[1]));
            }

            List<java.util.Map.Entry<String, Long>> sortedProds = new java.util.ArrayList<>(topProdMap.entrySet());
            sortedProds.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

            ObjectNode topProductsNode = resp.putObject("topProducts");
            ArrayNode tpLabels = topProductsNode.putArray("labels");
            ArrayNode tpValues = topProductsNode.putArray("values");
            int limit = 0;
            for (java.util.Map.Entry<String, Long> entry : sortedProds) {
                if (limit >= 5) break;
                tpLabels.add(entry.getKey());
                tpValues.add(entry.getValue());
                limit++;
            }

            // ── 6. New Customer Registrations Over Time ───────────────────────
            DateTimeFormatter custFmt = ("monthly".equals(period) || (start != null && !start.isBlank())) ? DateTimeFormatter.ofPattern("yyyy-MM") : DateTimeFormatter.ofPattern("yyyy-MM-dd");
            List<Object[]> custUsers = em.createQuery(
                    "SELECT u.createdAt FROM User u WHERE u.role='CUSTOMER' AND u.createdAt BETWEEN :s AND :e ORDER BY u.createdAt ASC",
                    Object[].class).setParameter("s", startDt).setParameter("e", endDt).getResultList();

            java.util.Map<String, Long> custMap = new java.util.TreeMap<>();
            for (Object[] r : custUsers) {
                if (r[0] != null) {
                    String label = ((LocalDateTime) r[0]).format(custFmt);
                    custMap.put(label, custMap.getOrDefault(label, 0L) + 1L);
                }
            }

            ObjectNode customerChart = resp.putObject("customerChart");
            ArrayNode custLabels = customerChart.putArray("labels");
            ArrayNode custValues = customerChart.putArray("values");
            for (java.util.Map.Entry<String, Long> entry : custMap.entrySet()) {
                custLabels.add(entry.getKey());
                custValues.add(entry.getValue());
            }

            // ── 7. Material Usage Chart Data ──────────────────────────────────
            List<Object[]> matUsageList = em.createQuery(
                    "SELECT log.packingMaterial.name, SUM(ABS(log.quantityChange)) FROM PackingMaterialLog log " +
                    "WHERE log.quantityChange < 0 AND log.createdAt BETWEEN :s AND :e " +
                    "GROUP BY log.packingMaterial.name ORDER BY SUM(ABS(log.quantityChange)) DESC", Object[].class)
                    .setParameter("s", startDt).setParameter("e", endDt).getResultList();

            ObjectNode materialUsageNode = resp.putObject("materialUsage");
            ArrayNode muLabels = materialUsageNode.putArray("labels");
            ArrayNode muValues = materialUsageNode.putArray("values");
            for (Object[] row : matUsageList) {
                muLabels.add(row[0] != null ? row[0].toString() : "Unknown");
                muValues.add(safeLong(row[1]));
            }

            // ── Today's Orders Status (Web + POS) ───────────────────────────────
            LocalDateTime todayStart = LocalDate.now().atStartOfDay();
            LocalDateTime todayEnd = LocalDate.now().atTime(23, 59, 59);
            List<Object[]> todayWebData = em.createQuery(
                    "SELECT o.orderStatus, COUNT(o) FROM Order o WHERE o.createdAt BETWEEN :s AND :e GROUP BY o.orderStatus",
                    Object[].class).setParameter("s", todayStart).setParameter("e", todayEnd).getResultList();
            List<Object[]> todayPosData = em.createQuery(
                    "SELECT po.orderStatus, COUNT(po) FROM PosOrder po WHERE po.createdAt BETWEEN :s AND :e GROUP BY po.orderStatus",
                    Object[].class).setParameter("s", todayStart).setParameter("e", todayEnd).getResultList();
            
            java.util.Map<String, Long> todayStatusMap = new java.util.HashMap<>();
            for (Object[] row : todayWebData) {
                String st = row[0] != null ? row[0].toString() : "UNKNOWN";
                todayStatusMap.put(st, todayStatusMap.getOrDefault(st, 0L) + safeLong(row[1]));
            }
            for (Object[] row : todayPosData) {
                String st = row[0] != null ? row[0].toString() : "UNKNOWN";
                todayStatusMap.put(st, todayStatusMap.getOrDefault(st, 0L) + safeLong(row[1]));
            }

            if (todayStatusMap.isEmpty()) {
                // Fallback to lifetime orders status breakdown if no orders created today
                List<Object[]> ltWeb = em.createQuery("SELECT o.orderStatus, COUNT(o) FROM Order o GROUP BY o.orderStatus", Object[].class).getResultList();
                List<Object[]> ltPos = em.createQuery("SELECT po.orderStatus, COUNT(po) FROM PosOrder po GROUP BY po.orderStatus", Object[].class).getResultList();
                for (Object[] row : ltWeb) {
                    String st = row[0] != null ? row[0].toString() : "UNKNOWN";
                    todayStatusMap.put(st, todayStatusMap.getOrDefault(st, 0L) + safeLong(row[1]));
                }
                for (Object[] row : ltPos) {
                    String st = row[0] != null ? row[0].toString() : "UNKNOWN";
                    todayStatusMap.put(st, todayStatusMap.getOrDefault(st, 0L) + safeLong(row[1]));
                }
            }
            
            ObjectNode todayOrders = resp.putObject("todayOrders");
            ArrayNode toLabels = todayOrders.putArray("labels");
            ArrayNode toValues = todayOrders.putArray("values");
            for (java.util.Map.Entry<String, Long> entry : todayStatusMap.entrySet()) {
                toLabels.add(entry.getKey());
                toValues.add(entry.getValue());
            }

            // ── Sales Rep Performance (Web + POS) ──────────────────────────────
            List<Object[]> repData = em.createQuery(
                    "SELECT COALESCE(u.fullName, 'Sales Staff'), COUNT(po), SUM(po.totalAmount) FROM PosOrder po " +
                    "LEFT JOIN po.salesRep u " +
                    "WHERE po.orderStatus NOT IN ('CANCELLED', 'RETURNED') AND po.createdAt BETWEEN :s AND :e " +
                    "GROUP BY COALESCE(u.fullName, 'Sales Staff') ORDER BY SUM(po.totalAmount) DESC", Object[].class)
                    .setParameter("s", startDt).setParameter("e", endDt).getResultList();

            if (repData.isEmpty()) {
                // Fallback to lifetime sales rep performance if empty in current window
                repData = em.createQuery(
                        "SELECT COALESCE(u.fullName, 'Sales Staff'), COUNT(po), SUM(po.totalAmount) FROM PosOrder po " +
                        "LEFT JOIN po.salesRep u " +
                        "WHERE po.orderStatus NOT IN ('CANCELLED', 'RETURNED') " +
                        "GROUP BY COALESCE(u.fullName, 'Sales Staff') ORDER BY SUM(po.totalAmount) DESC", Object[].class)
                        .getResultList();
            }

            ObjectNode salesRepChart = resp.putObject("salesRepChart");
            ArrayNode repLabels = salesRepChart.putArray("labels");
            ArrayNode repCountValues = salesRepChart.putArray("countValues");
            ArrayNode repRevValues = salesRepChart.putArray("revValues");
            for (Object[] row : repData) {
                repLabels.add(row[0] != null ? row[0].toString() : "Sales Staff");
                repCountValues.add(safeLong(row[1]));
                repRevValues.add(safeDouble(row[2]));
            }

            // ── Sales Ledger Table (Web + POS Realized & Receivables) ─────────
            List<Object[]> webLedger = em.createQuery(
                    "SELECT o.createdAt, o.createdAt, o.id, COALESCE(u.fullName, 'Guest'), " +
                    "COALESCE((SELECT p.paymentMethod FROM Payment p WHERE p.order.id = o.id ORDER BY p.id DESC), 'COD'), " +
                    "COALESCE((SELECT p.slipImageUrl FROM Payment p WHERE p.order.id = o.id ORDER BY p.id DESC), ''), " +
                    "o.totalAmount, COALESCE(o.paymentStatus, 'PENDING'), 'WEB', o.orderStatus, " +
                    "COALESCE((SELECT p.transactionId FROM Payment p WHERE p.order.id = o.id ORDER BY p.id DESC), '') " +
                    "FROM Order o LEFT JOIN o.user u WHERE o.createdAt BETWEEN :s AND :e ORDER BY o.createdAt DESC",
                    Object[].class)
                    .setParameter("s", startDt).setParameter("e", endDt)
                    .setMaxResults(200).getResultList();

            List<Object[]> posLedger = em.createQuery(
                    "SELECT po.createdAt, po.createdAt, po.id, COALESCE(po.customerName, 'Walk-in Customer'), " +
                    "po.paymentMethod, COALESCE(po.paymentSlipUrl, ''), po.totalAmount, " +
                    "po.orderStatus, 'POS', po.advancePaid, po.codBalance, '' " +
                    "FROM PosOrder po WHERE po.createdAt BETWEEN :s AND :e ORDER BY po.createdAt DESC",
                    Object[].class)
                    .setParameter("s", startDt).setParameter("e", endDt)
                    .setMaxResults(200).getResultList();

            List<Object[]> mergedLedger = new java.util.ArrayList<>();
            mergedLedger.addAll(webLedger);
            mergedLedger.addAll(posLedger);
            mergedLedger.sort((a, b) -> {
                LocalDateTime dtA = (LocalDateTime) a[0];
                LocalDateTime dtB = (LocalDateTime) b[0];
                if (dtA == null && dtB == null) return 0;
                if (dtA == null) return 1;
                if (dtB == null) return -1;
                return dtB.compareTo(dtA);
            });
            if (mergedLedger.size() > 200) mergedLedger = mergedLedger.subList(0, 200);

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            ArrayNode ledgerRows = resp.putArray("ledger");
            for (Object[] row : mergedLedger) {
                ObjectNode r = ledgerRows.addObject();
                r.put("paymentDate", row[0] != null ? ((LocalDateTime) row[0]).format(dtf) : "");
                r.put("orderDate",   row[1] != null ? ((LocalDateTime) row[1]).format(dtf) : "");
                boolean isPos = "POS".equals(row[8]);
                Integer rawId = (Integer) row[2];
                r.put("orderId",     row[2] != null ? (isPos ? "POS-" : "CLC-") + String.format("%05d", rawId) : "");
                r.put("customer",    row[3] != null ? row[3].toString() : "Guest");
                String method = row[4] != null ? row[4].toString() : "";
                r.put("paymentMethod", method);
                String methodLabel = switch (method) {
                    case "COD" -> "Cash on Delivery";
                    case "BANK_TRANSFER" -> "Bank Transfer";
                    case "PAYHERE", "CARD", "STRIPE" -> "Card Payment";
                    case "COD_WITH_DEPOSIT", "COD_WITH_BANK_DEPOSIT", "ADVANCE_COD" -> "Advance Paid + COD";
                    case "FULL_PAID" -> "Full Paid (POS/Slip)";
                    default -> method;
                };
                r.put("paymentMethodLabel", methodLabel);

                String rawSlip = row[5] != null ? row[5].toString().trim() : "";
                String rawTx = row.length > 10 && row[10] != null ? row[10].toString().trim() : "";

                // Generate clean systematic Transaction ID
                String txId = rawTx;
                if (txId.isEmpty() || txId.startsWith("http")) {
                    if (isPos) {
                        String pfx = switch (method) {
                            case "BANK_TRANSFER" -> "TXN-BNK-POS-";
                            case "COD" -> "TXN-COD-POS-";
                            case "FULL_PAID" -> "TXN-POS-";
                            case "COD_WITH_DEPOSIT", "COD_WITH_BANK_DEPOSIT", "ADVANCE_COD" -> "TXN-ADV-POS-";
                            default -> "TXN-POS-";
                        };
                        txId = pfx + String.format("%05d", rawId);
                    } else {
                        String pfx = switch (method) {
                            case "PAYHERE", "CARD", "STRIPE" -> "TXN-CARD-";
                            case "BANK_TRANSFER" -> "TXN-BNK-";
                            case "COD" -> "TXN-COD-";
                            case "COD_WITH_DEPOSIT", "COD_WITH_BANK_DEPOSIT", "ADVANCE_COD" -> "TXN-ADV-";
                            default -> "TXN-";
                        };
                        txId = pfx + String.format("%05d", rawId);
                    }
                }

                r.put("transactionId", txId);
                r.put("slipUrl", (rawSlip.startsWith("http") || rawSlip.contains("cloudinary")) ? rawSlip : "");
                
                BigDecimal amt = row[6] != null ? (BigDecimal) row[6] : BigDecimal.ZERO;
                r.put("amount", amt.toPlainString());

                String finStatus = "PENDING";
                if (isPos) {
                    String oStatus = row[7] != null ? row[7].toString() : "PENDING";
                    BigDecimal adv = row[9] != null ? (BigDecimal) row[9] : BigDecimal.ZERO;
                    BigDecimal cod = row.length > 10 && row[10] instanceof BigDecimal ? (BigDecimal) row[10] : BigDecimal.ZERO;
                    if ("CANCELLED".equals(oStatus)) finStatus = "CANCELLED";
                    else if ("RETURNED".equals(oStatus)) finStatus = "RETURNED";
                    else if ("DELIVERED".equals(oStatus) || "COMPLETED".equals(oStatus)) finStatus = "PAID (DELIVERED)";
                    else if ("FULL_PAID".equals(method)) finStatus = "PAID";
                    else if (adv.compareTo(BigDecimal.ZERO) > 0) finStatus = "ADVANCE PAID (COD: LKR " + cod + ")";
                    else finStatus = "COD PENDING";
                } else {
                    String pStatus = row[7] != null ? row[7].toString() : "PENDING";
                    String oStatus = row[9] != null ? row[9].toString() : "PENDING";
                    if ("CANCELLED".equals(oStatus)) finStatus = "CANCELLED";
                    else if ("RETURNED".equals(oStatus)) finStatus = "RETURNED";
                    else if ("PAID".equals(pStatus)) finStatus = "PAID";
                    else if ("DELIVERED".equals(oStatus)) finStatus = "PAID (COD SETTLED)";
                    else finStatus = "PENDING";
                }
                r.put("status", finStatus);
            }

            return ResponseEntity.ok(mapper.writeValueAsString(resp));

        } catch (Exception e) {
            LOG.severe("Analytics error: " + e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("{\"success\":false,\"message\":\"" + e.getMessage().replace("\"", "'") + "\"}");
        }
    }
}
