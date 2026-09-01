package com.ceylonletterco.controller;

import com.ceylonletterco.entity.BusinessExpense;
import com.ceylonletterco.repository.BusinessExpenseRepository;
import com.ceylonletterco.service.AuditLogService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

@RestController
@RequestMapping("/api/admin/expenses")
public class AdminExpenseController {

    @Autowired
    private BusinessExpenseRepository expenseRepo;

    @Autowired
    private AuditLogService auditLogService;

    @PersistenceContext
    private EntityManager em;

    // ── Auth helper ────────────────────────────────────────────────────────────
    private boolean isAuthorized(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return false;
        String role = (String) session.getAttribute("userRole");
        if (role == null) return false;
        return role.contains("ADMIN") || role.contains("MANAGER");
    }

    // ── Expense category labels ────────────────────────────────────────────────
    private static final Map<String, String> CATEGORY_LABELS = new LinkedHashMap<>();
    static {
        CATEGORY_LABELS.put("TRANSPORT",              "Transport");
        CATEGORY_LABELS.put("DIGITAL_ADS",            "Digital Ads");
        CATEGORY_LABELS.put("DELIVERY_CHARGES",       "Delivery Charges");
        CATEGORY_LABELS.put("INTERNET_ELECTRICITY",   "Internet / Electricity");
        CATEGORY_LABELS.put("PACKAGING",              "Packaging");
        CATEGORY_LABELS.put("INVENTORY",              "Inventory");
        CATEGORY_LABELS.put("DOMAIN",                 "Domain");
        CATEGORY_LABELS.put("HOSTING",                "Hosting");
        CATEGORY_LABELS.put("WEBSITE",                "Website");
        CATEGORY_LABELS.put("PAYMENT_GATEWAY",        "Payment Gateway");
        CATEGORY_LABELS.put("BANK_CHARGES",           "Bank Charges");
        CATEGORY_LABELS.put("OTHER",                  "Other");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GET /api/admin/expenses/categories  – return category list for dropdowns
    // ──────────────────────────────────────────────────────────────────────────
    @GetMapping("/categories")
    public ResponseEntity<?> getCategories(HttpServletRequest request) {
        if (!isAuthorized(request)) return ResponseEntity.status(403).body(Map.of("success", false, "message", "Access denied"));
        List<Map<String, String>> list = new ArrayList<>();
        CATEGORY_LABELS.forEach((key, label) -> list.add(Map.of("value", key, "label", label)));
        return ResponseEntity.ok(Map.of("success", true, "categories", list));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GET /api/admin/expenses?start=YYYY-MM-DD&end=YYYY-MM-DD&period=monthly
    // ──────────────────────────────────────────────────────────────────────────
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<?> listExpenses(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(defaultValue = "monthly") String period,
            HttpServletRequest request) {

        if (!isAuthorized(request)) return ResponseEntity.status(403).body(Map.of("success", false, "message", "Access denied"));

        LocalDate[] range = resolveDateRange(start, end, period);
        LocalDate startDate = range[0];
        LocalDate endDate   = range[1];

        List<BusinessExpense> expenses = expenseRepo
                .findByExpenseDateBetweenOrderByExpenseDateDescCreatedAtDesc(startDate, endDate);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (BusinessExpense e : expenses) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id",          e.getId());
            row.put("expenseDate", e.getExpenseDate().toString());
            row.put("category",    e.getCategory());
            row.put("categoryLabel", CATEGORY_LABELS.getOrDefault(e.getCategory(), e.getCategory()));
            row.put("description", e.getDescription() != null ? e.getDescription() : "");
            row.put("amount",      e.getAmount());
            row.put("frequency",   e.getFrequency());
            row.put("notes",       e.getNotes() != null ? e.getNotes() : "");
            rows.add(row);
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("expenses", rows);
        resp.put("startDate", startDate.toString());
        resp.put("endDate",   endDate.toString());
        return ResponseEntity.ok(resp);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GET /api/admin/expenses/summary?start=&end=&period=
    // Returns: totalExpenses, categoryBreakdown, totalRevenue, netProfit
    // ──────────────────────────────────────────────────────────────────────────
    @GetMapping("/summary")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getSummary(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(defaultValue = "monthly") String period,
            HttpServletRequest request) {

        if (!isAuthorized(request)) return ResponseEntity.status(403).body(Map.of("success", false, "message", "Access denied"));

        LocalDate[] range = resolveDateRange(start, end, period);
        LocalDate startDate = range[0];
        LocalDate endDate   = range[1];

        LocalDateTime startDt = startDate.atStartOfDay();
        LocalDateTime endDt   = endDate.atTime(23, 59, 59);

        // ── Total Expenses ────────────────────────────────────────────────────
        BigDecimal totalExpenses = expenseRepo.sumAmountByDateRange(startDate, endDate);
        if (totalExpenses == null) totalExpenses = BigDecimal.ZERO;

        // ── Category Breakdown ────────────────────────────────────────────────
        List<Object[]> catRows = expenseRepo.sumByCategoryAndDateRange(startDate, endDate);
        List<Map<String, Object>> categoryBreakdown = new ArrayList<>();
        for (Object[] row : catRows) {
            String cat = (String) row[0];
            BigDecimal amt = row[1] instanceof BigDecimal ? (BigDecimal) row[1] : BigDecimal.valueOf(((Number) row[1]).doubleValue());
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("category",      cat);
            entry.put("categoryLabel", CATEGORY_LABELS.getOrDefault(cat, cat));
            entry.put("amount",        amt.setScale(2, RoundingMode.HALF_UP));
            double pct = totalExpenses.compareTo(BigDecimal.ZERO) > 0
                    ? amt.divide(totalExpenses, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue()
                    : 0.0;
            entry.put("percentage", Math.round(pct * 10.0) / 10.0);
            categoryBreakdown.add(entry);
        }

        // ── Total Revenue (Web Orders + POS Orders) ───────────────────────────
        Object webRevRaw = em.createQuery(
                "SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o " +
                "WHERE o.orderStatus NOT IN ('CANCELLED', 'RETURNED') " +
                "AND (o.paymentStatus = 'PAID' OR o.orderStatus = 'DELIVERED') " +
                "AND o.createdAt BETWEEN :s AND :e")
                .setParameter("s", startDt).setParameter("e", endDt).getSingleResult();

        double webRev = webRevRaw instanceof Number ? ((Number) webRevRaw).doubleValue() : 0.0;

        // POS orders revenue
        @SuppressWarnings("unchecked")
        List<Object[]> posRows = em.createQuery(
                "SELECT po.orderStatus, po.paymentMethod, po.totalAmount, po.advancePaid " +
                "FROM PosOrder po WHERE po.orderStatus NOT IN ('CANCELLED','RETURNED') " +
                "AND po.createdAt BETWEEN :s AND :e")
                .setParameter("s", startDt).setParameter("e", endDt).getResultList();

        double posRev = 0.0;
        for (Object[] row : posRows) {
            String st  = row[0] != null ? (String) row[0] : "PENDING";
            String pm  = row[1] != null ? (String) row[1] : "FULL_PAID";
            double tot = row[2] instanceof Number ? ((Number) row[2]).doubleValue() : 0.0;
            double adv = row[3] instanceof Number ? ((Number) row[3]).doubleValue() : 0.0;
            if ("DELIVERED".equals(st) || "COMPLETED".equals(st)) {
                posRev += tot;
            } else if ("FULL_PAID".equals(pm)) {
                posRev += tot;
            } else if (adv > 0) {
                posRev += adv;
            }
        }

        double totalRevenue = webRev + posRev;
        double netProfit    = totalRevenue - totalExpenses.doubleValue();
        double profitMargin = totalRevenue > 0 ? (netProfit / totalRevenue) * 100.0 : 0.0;

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success",           true);
        resp.put("startDate",         startDate.toString());
        resp.put("endDate",           endDate.toString());
        resp.put("totalRevenue",      round2(totalRevenue));
        resp.put("webRevenue",        round2(webRev));
        resp.put("posRevenue",        round2(posRev));
        resp.put("totalExpenses",     totalExpenses.setScale(2, RoundingMode.HALF_UP));
        resp.put("netProfit",         round2(netProfit));
        resp.put("profitMargin",      round2(profitMargin));
        resp.put("isProfitable",      netProfit >= 0);
        resp.put("categoryBreakdown", categoryBreakdown);
        return ResponseEntity.ok(resp);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // POST /api/admin/expenses  – create new expense
    // ──────────────────────────────────────────────────────────────────────────
    @PostMapping
    @Transactional
    public ResponseEntity<?> createExpense(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        if (!isAuthorized(request)) return ResponseEntity.status(403).body(Map.of("success", false, "message", "Access denied"));

        try {
            BusinessExpense e = new BusinessExpense();
            e.setCategory(requireString(body, "category"));
            e.setAmount(new BigDecimal(requireString(body, "amount")));
            e.setExpenseDate(LocalDate.parse(requireString(body, "expenseDate")));

            if (body.get("description") != null) e.setDescription(body.get("description").toString().trim());
            if (body.get("frequency")   != null) e.setFrequency(body.get("frequency").toString().trim());
            if (body.get("notes")       != null) e.setNotes(body.get("notes").toString().trim());

            BusinessExpense saved = expenseRepo.save(e);
            
            auditLogService.log(request, "ADD_EXPENSE", "EXPENSE",
                    "Added " + saved.getCategory() + " expense of LKR " + saved.getAmount() + (saved.getDescription() != null ? " (" + saved.getDescription() + ")" : ""),
                    "SUCCESS");

            return ResponseEntity.ok(Map.of("success", true, "message", "Expense saved", "id", saved.getId()));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", ex.getMessage()));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PUT /api/admin/expenses/{id}  – update expense
    // ──────────────────────────────────────────────────────────────────────────
    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> updateExpense(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        if (!isAuthorized(request)) return ResponseEntity.status(403).body(Map.of("success", false, "message", "Access denied"));

        Optional<BusinessExpense> opt = expenseRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.status(404).body(Map.of("success", false, "message", "Not found"));

        BusinessExpense e = opt.get();
        try {
            if (body.get("category")    != null) e.setCategory(body.get("category").toString());
            if (body.get("amount")      != null) e.setAmount(new BigDecimal(body.get("amount").toString()));
            if (body.get("expenseDate") != null) e.setExpenseDate(LocalDate.parse(body.get("expenseDate").toString()));
            if (body.get("description") != null) e.setDescription(body.get("description").toString().trim());
            if (body.get("frequency")   != null) e.setFrequency(body.get("frequency").toString().trim());
            if (body.get("notes")       != null) e.setNotes(body.get("notes").toString().trim());
            
            expenseRepo.save(e);
            
            auditLogService.log(request, "UPDATE_EXPENSE", "EXPENSE",
                    "Updated expense #" + id + " [" + e.getCategory() + "] LKR " + e.getAmount(),
                    "SUCCESS");

            return ResponseEntity.ok(Map.of("success", true, "message", "Updated"));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", ex.getMessage()));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // DELETE /api/admin/expenses/{id}
    // ──────────────────────────────────────────────────────────────────────────
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> deleteExpense(@PathVariable Long id, HttpServletRequest request) {
        if (!isAuthorized(request)) return ResponseEntity.status(403).body(Map.of("success", false, "message", "Access denied"));
        if (!expenseRepo.existsById(id)) return ResponseEntity.notFound().build();
        expenseRepo.deleteById(id);
        auditLogService.log(request, "DELETE_EXPENSE", "EXPENSE", "Deleted expense #" + id, "SUCCESS");
        return ResponseEntity.ok(Map.of("success", true, "message", "Deleted"));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────
    private LocalDate[] resolveDateRange(String start, String end, String period) {
        if (start != null && !start.isBlank() && end != null && !end.isBlank()) {
            return new LocalDate[]{LocalDate.parse(start), LocalDate.parse(end)};
        }
        LocalDate today = LocalDate.now();
        return switch (period) {
            case "daily"   -> new LocalDate[]{today, today};
            case "weekly"  -> new LocalDate[]{today.minusDays(6), today};
            case "yearly"  -> new LocalDate[]{today.withDayOfYear(1), today};
            default        -> { // monthly
                YearMonth ym = YearMonth.of(today.getYear(), today.getMonth());
                yield new LocalDate[]{ym.atDay(1), ym.atEndOfMonth()};
            }
        };
    }

    private String requireString(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) throw new IllegalArgumentException("Missing field: " + key);
        return v.toString().trim();
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
