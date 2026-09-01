package com.auracraft.controller;

import com.auracraft.entity.*;
import com.auracraft.service.CloudinaryService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * PosOrderController – handles all POS order operations for Sales Reps.
 * Endpoints under /api/pos/*
 */
@RestController
@RequestMapping("/api/pos")
@Transactional
public class PosOrderController {

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private CloudinaryService cloudinaryService;

    @Autowired
    private com.auracraft.service.NotificationService notificationService;

    @Autowired
    private com.auracraft.service.AuditLogService auditLogService;

    @Value("${app.upload.path:/uploads}")
    private String uploadPath;

    private User getStaffUser(HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        if (s == null) return null;
        User u = (User) s.getAttribute("loggedInUser");
        if (u == null) return null;
        String role = u.getRole() != null ? u.getRole().toUpperCase() : "";
        if (!"CUSTOMER".equals(role) && !role.isEmpty()) return u;
        return null;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    // ── GET /api/pos/products  — Product catalog for POS ───────────────────────
    @GetMapping(value = "/products", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<String> getProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer categoryId,
            HttpServletRequest request) {

        if (getStaffUser(request) == null)
            return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");

        try {
            String jpql = "SELECT DISTINCT p FROM Product p " +
                    "JOIN FETCH p.variants v " +
                    "LEFT JOIN p.images img " +
                    "LEFT JOIN p.category c " +
                    "WHERE p.isDeleted = false AND p.isActive = true " +
                    (categoryId != null ? "AND p.category.id = :catId " : "") +
                    (search != null && !search.isBlank() ? "AND LOWER(p.name) LIKE :search " : "") +
                    "ORDER BY p.name ASC";

            var query = em.createQuery(jpql, Product.class);
            if (categoryId != null) query.setParameter("catId", categoryId);
            if (search != null && !search.isBlank()) query.setParameter("search", "%" + search.toLowerCase() + "%");

            List<Product> products = query.getResultList();
            StringBuilder sb = new StringBuilder("[");
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            for (int i = 0; i < products.size(); i++) {
                Product p = products.get(i);
                if (i > 0) sb.append(",");
                // First image
                String imgUrl = "";
                if (!p.getImages().isEmpty()) imgUrl = p.getImages().get(0).getImageUrl();

                sb.append("{")
                    .append("\"id\":").append(p.getId()).append(",")
                    .append("\"name\":\"").append(esc(p.getName())).append("\",")
                    .append("\"sku\":\"").append(esc(p.getSku())).append("\",")
                    .append("\"basePrice\":").append(p.getBasePrice() != null ? p.getBasePrice() : 0).append(",")
                    .append("\"isCustomisable\":").append(p.getIsCustomisable() != null && p.getIsCustomisable()).append(",")
                    .append("\"categoryId\":").append(p.getCategory() != null ? p.getCategory().getId() : 0).append(",")
                    .append("\"category\":\"").append(p.getCategory() != null ? esc(p.getCategory().getName()) : "").append("\",")
                    .append("\"imageUrl\":\"").append(esc(imgUrl)).append("\",")
                    .append("\"variants\":[");

                List<ProductVariant> variants = p.getVariants();
                for (int j = 0; j < variants.size(); j++) {
                    ProductVariant v = variants.get(j);
                    if (v.getIsDeleted() != null && v.getIsDeleted()) continue;
                    if (j > 0) sb.append(",");

                    // Get stock
                    List<Inventory> invList = em.createQuery(
                            "SELECT i FROM Inventory i WHERE i.variantId = :vid", Inventory.class)
                            .setParameter("vid", v.getId()).getResultList();
                    int stock = invList.isEmpty() ? 0 : invList.get(0).getQuantityOnHand();

                    sb.append("{")
                        .append("\"id\":").append(v.getId()).append(",")
                        .append("\"sku\":\"").append(esc(v.getSkuVariant())).append("\",")
                        .append("\"metalColor\":\"").append(esc(v.getMetalColor())).append("\",")
                        .append("\"sizeLength\":\"").append(esc(v.getSizeLength())).append("\",")
                        .append("\"price\":").append(v.getPrice()).append(",")
                        .append("\"stock\":").append(stock)
                        .append("}");
                }
                sb.append("]}");
            }
            sb.append("]");
            return ResponseEntity.ok("{\"success\":true,\"products\":" + sb + "}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── GET /api/pos/categories ─────────────────────────────────────────────────
    @GetMapping(value = "/categories", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<String> getCategories(HttpServletRequest request) {
        if (getStaffUser(request) == null)
            return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");

        List<Category> cats = em.createQuery("SELECT c FROM Category c ORDER BY c.name ASC", Category.class).getResultList();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < cats.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"id\":").append(cats.get(i).getId())
              .append(",\"name\":\"").append(esc(cats.get(i).getName())).append("\"}");
        }
        sb.append("]");
        return ResponseEntity.ok("{\"success\":true,\"categories\":" + sb + "}");
    }

    private static BigDecimal parseDecimal(JsonNode node, String fieldName, BigDecimal defaultValue) {
        if (node == null || !node.has(fieldName) || node.get(fieldName).isNull()) {
            return defaultValue;
        }
        String val = node.get(fieldName).asText("").trim().replace(",", "");
        if (val.isEmpty()) return defaultValue;
        try {
            return new BigDecimal(val);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    // ── POST /api/pos/orders  — Create POS order ───────────────────────────────
    @PostMapping(value = "/orders", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createPosOrder(@RequestBody JsonNode body, HttpServletRequest request) {
        User staff = getStaffUser(request);
        if (staff == null)
            return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied. Please login again.\"}");

        try {
            PosOrder order = new PosOrder();
            order.setSalesRep(em.find(User.class, staff.getId()));
            order.setCustomerName(body.path("customerName").asText("").trim());
            order.setCustomerAddress(body.path("customerAddress").asText("").trim());
            order.setPhone1(body.path("phone1").asText("").trim());
            order.setPhone2(body.path("phone2").asText("").trim());
            order.setIsCustom(body.path("isCustom").asBoolean(false));
            order.setIsWarrantyReplacement(body.path("isWarrantyReplacement").asBoolean(false));
            order.setCustomNotes(body.path("customNotes").asText("").trim());
            order.setPaymentMethod(body.path("paymentMethod").asText("FULL_PAID").trim().toUpperCase());

            BigDecimal subtotal = parseDecimal(body, "subtotal", BigDecimal.ZERO);
            BigDecimal discount = parseDecimal(body, "discountAmount", BigDecimal.ZERO);
            BigDecimal delivery = parseDecimal(body, "deliveryCharge", BigDecimal.ZERO);
            BigDecimal total = subtotal.subtract(discount).add(delivery);
            if (total.compareTo(BigDecimal.ZERO) < 0) total = BigDecimal.ZERO;

            order.setSubtotal(subtotal);
            order.setDiscountAmount(discount);
            order.setDeliveryCharge(delivery);
            order.setTotalAmount(total);

            String pm = order.getPaymentMethod();
            if ("WARRANTY_CLAIM".equals(pm) || "FULL_PAID_WARRANTY".equals(pm) || Boolean.TRUE.equals(order.getIsWarrantyReplacement())) {
                order.setPaymentMethod("WARRANTY_CLAIM");
                order.setIsWarrantyReplacement(true);
                order.setSubtotal(subtotal);
                order.setDiscountAmount(subtotal); // 100% covered by warranty
                order.setDeliveryCharge(BigDecimal.ZERO);
                order.setTotalAmount(BigDecimal.ZERO);
                order.setAdvancePaid(BigDecimal.ZERO);
                order.setCodBalance(BigDecimal.ZERO);
                order.setOrderStatus("PROCESSING");
            } else if ("ADVANCE_COD".equals(pm)) {
                BigDecimal adv = parseDecimal(body, "advancePaid", BigDecimal.ZERO);
                order.setAdvancePaid(adv);
                order.setCodBalance(total.subtract(adv));
            } else if ("FULL_PAID".equals(pm)) {
                order.setAdvancePaid(total);
                order.setCodBalance(BigDecimal.ZERO);
            } else {
                order.setAdvancePaid(BigDecimal.ZERO);
                order.setCodBalance(total);
            }

            em.persist(order);
            em.flush();

            // Process items and decrement stock
            JsonNode items = body.path("items");
            // Base total cost is the 400 LKR fixed delivery charge for profit calculation
            BigDecimal orderTotalCost = BigDecimal.valueOf(400);

            if (items.isArray()) {
                for (JsonNode item : items) {
                    int variantId = item.path("variantId").asInt();
                    int qty = item.path("quantity").asInt(1);
                    BigDecimal unitPrice = parseDecimal(item, "unitPrice", BigDecimal.ZERO);

                    ProductVariant variant = em.find(ProductVariant.class, variantId);
                    if (variant == null) continue;

                    PosOrderItem poi = new PosOrderItem();
                    poi.setPosOrder(order);
                    poi.setProductVariant(variant);
                    poi.setQuantity(qty);
                    poi.setUnitPrice(unitPrice);
                    poi.setCostAtPurchase(variant.getCostPrice() != null ? variant.getCostPrice() : BigDecimal.ZERO);
                    poi.setEngravingText(item.path("engravingText").asText(""));
                    poi.setCustomResize(item.path("customResize").asText(""));
                    em.persist(poi);

                    orderTotalCost = orderTotalCost.add(poi.getCostAtPurchase().multiply(BigDecimal.valueOf(qty)));

                    // Decrement inventory
                    List<Inventory> invList = em.createQuery(
                            "SELECT i FROM Inventory i WHERE i.variantId = :vid", Inventory.class)
                            .setParameter("vid", variantId).getResultList();
                    if (!invList.isEmpty()) {
                        Inventory inv = invList.get(0);
                        inv.setQuantityOnHand(Math.max(0, inv.getQuantityOnHand() - qty));
                        em.merge(inv);
                    }
                }
            }

            order.setTotalCost(orderTotalCost);
            if (Boolean.TRUE.equals(order.getIsWarrantyReplacement())) {
                order.setReturnLoss(orderTotalCost);
                order.setReturnReason("Warranty Replacement");
            }
            em.merge(order);

            final int fOrderId = order.getId();
            final String fRepName = staff.getFullName();
            try {
                notificationService.notifyStaffByRole(java.util.List.of("ADMIN", "MANAGER"), "NEW_ORDER", "New POS Order #POS-" + String.format("%05d", fOrderId) + " created by " + fRepName, "/admin.html");
            } catch (Exception ignored) {}

            String paymentBreakdown = "Method: " + order.getPaymentMethod() + 
                    " | Total: LKR " + order.getTotalAmount() + 
                    " (Subtotal: LKR " + order.getSubtotal() + ", Discount: LKR " + order.getDiscountAmount() + ", Delivery: LKR " + order.getDeliveryCharge() + ")" +
                    " | Advance Paid: LKR " + order.getAdvancePaid() + 
                    " | Remaining COD Balance: LKR " + order.getCodBalance() + " payable on delivery";

            auditLogService.log(staff.getEmail(), staff.getRole(), "POS_SALE", "POS",
                    "POS Order #POS-" + String.format("%05d", fOrderId) + " created by " + fRepName + " (" + staff.getEmail() + ") | " + paymentBreakdown + " [Customer: " + order.getCustomerName() + ", Phone: " + order.getPhone1() + "]",
                    com.auracraft.service.AuditLogService.extractClientIp(request),
                    com.auracraft.service.AuditLogService.extractUserAgent(request),
                    "SUCCESS");

            return ResponseEntity.ok("{\"success\":true,\"message\":\"POS order created successfully!\",\"orderId\":" + order.getId() + "}");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("{\"success\":false,\"message\":\"" + esc(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()) + "\"}");
        }
    }

    // ── POST /api/pos/orders/{id}/slip — Upload payment slip ───────────────────
    @PostMapping(value = "/orders/{id}/slip", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> uploadSlip(@PathVariable int id,
                                             @RequestParam("file") MultipartFile file,
                                             HttpServletRequest request) {
        User staff = getStaffUser(request);
        if (staff == null)
            return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");

        try {
            PosOrder order = em.find(PosOrder.class, id);
            if (order == null) return ResponseEntity.status(404).body("{\"success\":false,\"message\":\"Order not found.\"}");

            String url = cloudinaryService.uploadImage(file);
            order.setPaymentSlipUrl(url);
            em.merge(order);

            auditLogService.log(staff.getEmail(), staff.getRole(), "UPLOAD_PAYMENT_SLIP", "POS",
                    "Payment slip uploaded for POS Order #POS-" + String.format("%05d", id) + " (Customer: " + order.getCustomerName() + ") by " + staff.getEmail(),
                    com.auracraft.service.AuditLogService.extractClientIp(request),
                    com.auracraft.service.AuditLogService.extractUserAgent(request),
                    "SUCCESS");

            return ResponseEntity.ok("{\"success\":true,\"url\":\"" + url + "\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── GET /api/pos/orders — Orders for this sales rep ───────────────────────
    @GetMapping(value = "/orders", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<String> getMyOrders(
            @RequestParam(required = false) String date,
            HttpServletRequest request) {

        User staff = getStaffUser(request);
        if (staff == null)
            return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");

        try {
            String role = staff.getRole().toUpperCase();
            boolean isAdmin = role.contains("ADMIN") || role.contains("MANAGER");

            List<PosOrder> orders;
            if (isAdmin) {
                orders = em.createQuery("SELECT o FROM PosOrder o ORDER BY o.createdAt DESC", PosOrder.class)
                        .setMaxResults(200).getResultList();
            } else {
                orders = em.createQuery("SELECT o FROM PosOrder o WHERE o.salesRep.id = :uid ORDER BY o.createdAt DESC", PosOrder.class)
                        .setParameter("uid", staff.getId()).setMaxResults(200).getResultList();
            }

            StringBuilder sb = new StringBuilder("[");
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            for (int i = 0; i < orders.size(); i++) {
                if (i > 0) sb.append(",");
                PosOrder o = orders.get(i);
                List<PosOrderItem> items = em.createQuery(
                        "SELECT oi FROM PosOrderItem oi JOIN FETCH oi.productVariant v JOIN FETCH v.product WHERE oi.posOrder.id = :oid",
                        PosOrderItem.class).setParameter("oid", o.getId()).getResultList();

                sb.append("{")
                    .append("\"id\":").append(o.getId()).append(",")
                    .append("\"orderCode\":\"POS-").append(String.format("%05d", o.getId())).append("\",")
                    .append("\"customerName\":\"").append(esc(o.getCustomerName())).append("\",")
                    .append("\"customerAddress\":\"").append(esc(o.getCustomerAddress())).append("\",")
                    .append("\"phone1\":\"").append(esc(o.getPhone1())).append("\",")
                    .append("\"phone2\":\"").append(esc(o.getPhone2())).append("\",")
                    .append("\"subtotal\":").append(o.getSubtotal()).append(",")
                    .append("\"discountAmount\":").append(o.getDiscountAmount()).append(",")
                    .append("\"deliveryCharge\":").append(o.getDeliveryCharge()).append(",")
                    .append("\"totalAmount\":").append(o.getTotalAmount()).append(",")
                    .append("\"paymentMethod\":\"").append(esc(o.getPaymentMethod())).append("\",")
                    .append("\"advancePaid\":").append(o.getAdvancePaid()).append(",")
                    .append("\"codBalance\":").append(o.getCodBalance()).append(",")
                    .append("\"paymentSlipUrl\":").append(o.getPaymentSlipUrl() != null ? "\"" + esc(o.getPaymentSlipUrl()) + "\"" : "null").append(",")
                    .append("\"orderStatus\":\"").append(esc(o.getOrderStatus())).append("\",")
                    .append("\"isCustom\":").append(o.getIsCustom() != null && o.getIsCustom()).append(",")
                    .append("\"customNotes\":\"").append(esc(o.getCustomNotes())).append("\",")
                    .append("\"trackingNumber\":\"").append(esc(o.getTrackingNumber())).append("\",")
                    .append("\"salesRepName\":\"").append(esc(o.getSalesRep().getFullName())).append("\",")
                    .append("\"salesRepEmail\":\"").append(esc(o.getSalesRep().getEmail())).append("\",")
                    .append("\"createdAt\":\"").append(o.getCreatedAt() != null ? o.getCreatedAt().format(dtf) : "").append("\",")
                    .append("\"items\":[");

                for (int j = 0; j < items.size(); j++) {
                    if (j > 0) sb.append(",");
                    PosOrderItem oi = items.get(j);
                    sb.append("{")
                        .append("\"variantId\":").append(oi.getProductVariant().getId()).append(",")
                        .append("\"productName\":\"").append(esc(oi.getProductVariant().getProduct().getName())).append("\",")
                        .append("\"variant\":\"").append(esc(oi.getProductVariant().getMetalColor())).append(" / ").append(esc(oi.getProductVariant().getSizeLength())).append("\",")
                        .append("\"quantity\":").append(oi.getQuantity()).append(",")
                        .append("\"unitPrice\":").append(oi.getUnitPrice()).append(",")
                        .append("\"engravingText\":\"").append(esc(oi.getEngravingText())).append("\",")
                        .append("\"customResize\":\"").append(esc(oi.getCustomResize())).append("\"")
                        .append("}");
                }
                sb.append("]}");
            }
            sb.append("]");
            return ResponseEntity.ok("{\"success\":true,\"orders\":" + sb + "}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── GET /api/pos/stats — Sales Rep personal statistics ────────────────────
    @GetMapping(value = "/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<String> getMyStats(HttpServletRequest request) {
        User staff = getStaffUser(request);
        if (staff == null)
            return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");

        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
            LocalDateTime startOfMonth = now.toLocalDate().withDayOfMonth(1).atStartOfDay();
            LocalDateTime startOfWeek = now.toLocalDate().minusDays(now.getDayOfWeek().getValue() - 1).atStartOfDay();

            Long todayCount = em.createQuery("SELECT COUNT(o) FROM PosOrder o WHERE o.salesRep.id = :uid AND o.createdAt >= :start", Long.class)
                    .setParameter("uid", staff.getId()).setParameter("start", startOfDay).getSingleResult();
            Long weekCount = em.createQuery("SELECT COUNT(o) FROM PosOrder o WHERE o.salesRep.id = :uid AND o.createdAt >= :start", Long.class)
                    .setParameter("uid", staff.getId()).setParameter("start", startOfWeek).getSingleResult();
            Long monthCount = em.createQuery("SELECT COUNT(o) FROM PosOrder o WHERE o.salesRep.id = :uid AND o.createdAt >= :start", Long.class)
                    .setParameter("uid", staff.getId()).setParameter("start", startOfMonth).getSingleResult();

            BigDecimal monthRevenue = (BigDecimal) em.createQuery("SELECT COALESCE(SUM(o.totalAmount), 0) FROM PosOrder o WHERE o.salesRep.id = :uid AND o.createdAt >= :start")
                    .setParameter("uid", staff.getId()).setParameter("start", startOfMonth).getSingleResult();

            return ResponseEntity.ok("{\"success\":true," +
                    "\"todayOrders\":" + todayCount + "," +
                    "\"weekOrders\":" + weekCount + "," +
                    "\"monthOrders\":" + monthCount + "," +
                    "\"monthRevenue\":" + monthRevenue + "}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── POST /api/pos/requests — Submit a Sales Rep request ───────────────────
    @PostMapping(value = "/requests", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> submitRequest(@RequestBody JsonNode body, HttpServletRequest request) {
        User staff = getStaffUser(request);
        if (staff == null)
            return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");

        try {
            SalesRepRequest req = new SalesRepRequest();
            req.setRequester(staff);
            req.setRequestType(body.path("requestType").asText("OTHER"));
            req.setProductReference(body.path("productReference").asText(""));
            req.setNotes(body.path("notes").asText(""));
            em.persist(req);
            return ResponseEntity.ok("{\"success\":true,\"message\":\"Request submitted successfully.\",\"id\":" + req.getId() + "}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── GET /api/pos/requests — Get my requests ────────────────────────────────
    @GetMapping(value = "/requests", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<String> getMyRequests(HttpServletRequest request) {
        User staff = getStaffUser(request);
        if (staff == null)
            return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");

        try {
            String role = staff.getRole().toUpperCase();
            boolean isAdmin = role.contains("ADMIN") || role.contains("MANAGER");
            List<SalesRepRequest> reqs;
            if (isAdmin) {
                reqs = em.createQuery("SELECT r FROM SalesRepRequest r ORDER BY r.createdAt DESC", SalesRepRequest.class).getResultList();
            } else {
                reqs = em.createQuery("SELECT r FROM SalesRepRequest r WHERE r.requester.id = :uid ORDER BY r.createdAt DESC", SalesRepRequest.class)
                        .setParameter("uid", staff.getId()).getResultList();
            }
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < reqs.size(); i++) {
                if (i > 0) sb.append(",");
                SalesRepRequest r = reqs.get(i);
                sb.append("{")
                    .append("\"id\":").append(r.getId()).append(",")
                    .append("\"requesterName\":\"").append(esc(r.getRequester().getFullName())).append("\",")
                    .append("\"requestType\":\"").append(esc(r.getRequestType())).append("\",")
                    .append("\"productReference\":\"").append(esc(r.getProductReference())).append("\",")
                    .append("\"notes\":\"").append(esc(r.getNotes())).append("\",")
                    .append("\"status\":\"").append(esc(r.getStatus())).append("\",")
                    .append("\"adminReply\":").append(r.getAdminReply() != null ? "\"" + esc(r.getAdminReply()) + "\"" : "null").append(",")
                    .append("\"createdAt\":\"").append(r.getCreatedAt() != null ? r.getCreatedAt().format(dtf) : "").append("\"")
                    .append("}");
            }
            sb.append("]");
            return ResponseEntity.ok("{\"success\":true,\"requests\":" + sb + "}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── PUT /api/pos/requests/{id}/resolve — Admin resolve request ─────────────
    @PutMapping(value = "/requests/{id}/resolve", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> resolveRequest(@PathVariable int id, @RequestBody JsonNode body, HttpServletRequest request) {
        User staff = getStaffUser(request);
        if (staff == null) return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");
        String role = staff.getRole().toUpperCase();
        if (!role.contains("ADMIN") && !role.contains("MANAGER"))
            return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Admin only.\"}");

        try {
            SalesRepRequest req = em.find(SalesRepRequest.class, id);
            if (req == null) return ResponseEntity.status(404).body("{\"success\":false,\"message\":\"Not found.\"}");
            req.setStatus(body.path("status").asText("RESOLVED"));
            req.setAdminReply(body.path("reply").asText(""));
            req.setResolvedBy(staff);
            req.setResolvedAt(LocalDateTime.now());
            em.merge(req);
            return ResponseEntity.ok("{\"success\":true,\"message\":\"Request updated.\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }
}
