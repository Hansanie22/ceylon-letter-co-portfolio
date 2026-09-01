package com.ceylonletterco.controller;

import com.ceylonletterco.entity.*;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * OrderController – migrated from OrderServlet.
 * Handles /api/orders/* endpoints.
 */
@RestController
@RequestMapping("/api/orders")
@Transactional
public class OrderController {

    @PersistenceContext
    private EntityManager em;
    
    @org.springframework.beans.factory.annotation.Autowired
    private com.ceylonletterco.service.EmailVerificationService emailService;

    @org.springframework.beans.factory.annotation.Autowired
    private com.ceylonletterco.service.NotificationService notificationService;

    @org.springframework.beans.factory.annotation.Autowired
    private com.ceylonletterco.service.AuditLogService auditLogService;

    // ── GET /api/orders ──────────────────────────────────────────────────────
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<String> getOrders(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        User user = (session != null) ? (User) session.getAttribute("loggedInUser") : null;
        if (user == null) return ResponseEntity.status(401).body("{\"success\":false,\"message\":\"Not authenticated\"}");

        try {
            List<Order> orders = em.createQuery(
                    "SELECT o FROM Order o WHERE o.user.id = :uid ORDER BY o.createdAt DESC", Order.class)
                    .setParameter("uid", user.getId())
                    .getResultList();

            StringBuilder arr = new StringBuilder("[");
            for (int i = 0; i < orders.size(); i++) {
                if (i > 0) arr.append(",");
                arr.append(orderToJson(orders.get(i)));
            }
            arr.append("]");
            return ResponseEntity.ok("{\"success\":true,\"orders\":" + arr + "}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── GET /api/orders/{id} ─────────────────────────────────────────────────
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<String> getOrder(@PathVariable int id, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        User user = (session != null) ? (User) session.getAttribute("loggedInUser") : null;
        if (user == null) return ResponseEntity.status(401).body("{\"success\":false,\"message\":\"Not authenticated\"}");

        try {
            Order order = em.find(Order.class, id);
            if (order == null || (!order.getUser().getId().equals(user.getId())
                    && "CUSTOMER".equalsIgnoreCase(user.getRole()))) {
                return ResponseEntity.status(404).body("{\"success\":false,\"message\":\"Order not found.\"}");
            }

            List<OrderItem> items = em.createQuery(
                    "SELECT oi FROM OrderItem oi JOIN FETCH oi.productVariant v JOIN FETCH v.product WHERE oi.order.id = :oid",
                    OrderItem.class).setParameter("oid", id).getResultList();

            StringBuilder itemArr = new StringBuilder("[");
            for (int i = 0; i < items.size(); i++) {
                if (i > 0) itemArr.append(",");
                OrderItem oi = items.get(i);
                ProductVariant v = oi.getProductVariant();
                String img = getPrimaryImage(v.getProduct().getId());
                itemArr.append("{")
                    .append("\"id\":").append(oi.getId()).append(",")
                    .append("\"productId\":").append(v.getProduct().getId()).append(",")
                    .append("\"variantId\":").append(v.getId()).append(",")
                    .append("\"name\":\"").append(esc(v.getProduct().getName())).append("\",")
                    .append("\"color\":\"").append(esc(v.getMetalColor())).append("\",")
                    .append("\"size\":\"").append(esc(v.getSizeLength())).append("\",")
                    .append("\"quantity\":").append(oi.getQuantity()).append(",")
                    .append("\"price\":").append(oi.getPriceAtPurchase()).append(",")
                    .append("\"imageUrl\":\"").append(esc(img)).append("\",")
                    .append("\"engravingText\":\"").append(esc(oi.getEngravingText())).append("\",")
                    .append("\"customResize\":\"").append(esc(oi.getCustomResize())).append("\"")
                    .append("}");
            }
            itemArr.append("]");

            String orderJson = orderToJson(order);
            // orderJson is something like {"id":1, "orderStatus":"...", ...}
            // We need to inject "success":true and "items":[...] inside it.
            orderJson = "{\"success\":true," + orderJson.substring(1, orderJson.length() - 1) + ",\"items\":" + itemArr + "}";

            return ResponseEntity.ok(orderJson);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── POST /api/orders ────────────────────────────────────────────────
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> placeOrder(@RequestBody JsonNode body, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        User user = (session != null) ? (User) session.getAttribute("loggedInUser") : null;
        if (user == null) return ResponseEntity.status(401).body("{\"success\":false,\"message\":\"Not authenticated\"}");

        try {
            User persistentUser = em.find(User.class, user.getId());

            // If user logged in with Phone or has missing/placeholder email, save newly entered checkout email
            String typedEmail = body.path("email").asText("").trim();
            String typedPhone = body.path("phone").asText("").trim();
            String typedFirstName = body.path("firstName").asText("").trim();
            String typedLastName = body.path("lastName").asText("").trim();

            boolean userUpdated = false;
            if (!typedEmail.isEmpty()) {
                if (persistentUser.getEmail() == null || persistentUser.getEmail().isBlank() 
                        || persistentUser.getEmail().contains("@phone.ceylonletterco.com") 
                        || persistentUser.getEmail().startsWith("phone_")) {
                    persistentUser.setEmail(typedEmail);
                    persistentUser.setEmailVerified(true);
                    userUpdated = true;
                }
            }
            if (!typedPhone.isEmpty() && (persistentUser.getPhone() == null || persistentUser.getPhone().isBlank())) {
                persistentUser.setPhone(typedPhone);
                userUpdated = true;
            }
            String fullNameInput = (typedFirstName + " " + typedLastName).trim();
            if (!fullNameInput.isEmpty()) {
                persistentUser.setFullName(fullNameInput);
                userUpdated = true;
            }
            if (userUpdated) {
                em.merge(persistentUser);
                session.setAttribute("loggedInUser", persistentUser);
                user = persistentUser;
            }

            int shippingAddressId = body.path("shippingAddressId").asInt(0);
            int billingAddressId = body.path("billingAddressId").asInt(shippingAddressId);
            String paymentMethod = body.path("paymentMethod").asText("COD");
            String slipImageUrl = body.path("slipImageUrl").asText(null);
            if (slipImageUrl != null && slipImageUrl.isEmpty()) slipImageUrl = null;
            JsonNode items = body.path("items");

            if (!items.isArray() || items.isEmpty()) {
                return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"Cart is empty.\"}");
            }

            Address shippingAddr = null;
            if (shippingAddressId > 0) {
                shippingAddr = em.find(Address.class, shippingAddressId);
            } else if (!body.path("shippingAddress").isMissingNode() && !body.path("shippingAddress").isNull()) {
                JsonNode sNode = body.path("shippingAddress");
                shippingAddr = new Address();
                shippingAddr.setUser(em.find(User.class, user.getId()));
                shippingAddr.setFullName(body.path("firstName").asText("") + " " + body.path("lastName").asText(""));
                shippingAddr.setStreet(sNode.path("addressLine1").asText(""));
                shippingAddr.setDistrict(sNode.path("addressLine2").asText(""));
                shippingAddr.setCity(sNode.path("city").asText(""));
                shippingAddr.setPostalCode(sNode.path("postalCode").asText(""));
                shippingAddr.setPhone(body.path("phone").asText(""));
                em.persist(shippingAddr);
            }

            if (shippingAddr == null) {
                return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"Shipping address not found or invalid.\"}");
            }

            Address billingAddr = null;
            boolean billingSameAsShipping = body.path("billingAddressSameAsShipping").asBoolean(true);
            if (billingSameAsShipping) {
                billingAddr = shippingAddr;
            } else if (billingAddressId > 0) {
                billingAddr = em.find(Address.class, billingAddressId);
            } else if (!body.path("billingAddress").isMissingNode() && !body.path("billingAddress").isNull()) {
                JsonNode bNode = body.path("billingAddress");
                billingAddr = new Address();
                billingAddr.setUser(em.find(User.class, user.getId()));
                billingAddr.setFullName(body.path("firstName").asText("") + " " + body.path("lastName").asText(""));
                billingAddr.setStreet(bNode.path("addressLine1").asText(""));
                billingAddr.setDistrict(bNode.path("addressLine2").asText(""));
                billingAddr.setCity(bNode.path("city").asText(""));
                billingAddr.setPostalCode(bNode.path("postalCode").asText(""));
                billingAddr.setPhone(body.path("phone").asText(""));
                em.persist(billingAddr);
            }
            if (billingAddr == null) billingAddr = shippingAddr;

            // Calculate totals
            BigDecimal total = BigDecimal.ZERO;
            BigDecimal shippingFee = BigDecimal.valueOf(350);
            BigDecimal tax = BigDecimal.ZERO;

            Order order = new Order();
            order.setUser(em.find(User.class, user.getId()));
            order.setShippingAddress(shippingAddr);
            order.setBillingAddress(billingAddr);
            String initialPaymentStatus = (slipImageUrl != null) ? "PENDING_VERIFICATION" : "PENDING";
            order.setOrderStatus("PENDING");
            order.setPaymentStatus(initialPaymentStatus);
            order.setShippingFee(shippingFee);
            order.setTax(tax);
            String customNotes = body.path("customNotes").asText("").trim();
            if (!customNotes.isEmpty()) order.setCustomNotes(customNotes);

            for (JsonNode item : items) {
                int variantId = item.path("variantId").asInt(0);
                int quantity = item.path("quantity").asInt(1);
                ProductVariant variant = em.find(ProductVariant.class, variantId);
                if (variant == null) continue;
                BigDecimal price = variant.getPrice() != null ? variant.getPrice() : BigDecimal.ZERO;
                total = total.add(price.multiply(BigDecimal.valueOf(quantity)));
            }

            order.setTotalAmount(total.add(shippingFee).add(tax));
            em.persist(order);
            em.flush();

            // Base total cost is the 400 LKR fixed delivery charge for profit calculation
            BigDecimal orderTotalCost = BigDecimal.valueOf(400);

            // Persist order items
            for (JsonNode item : items) {
                int variantId = item.path("variantId").asInt(0);
                int quantity = item.path("quantity").asInt(1);
                String engravingText = item.path("engravingText").asText("");
                String customResize = item.path("customResize").asText("");
                ProductVariant variant = em.find(ProductVariant.class, variantId);
                if (variant == null) continue;
                BigDecimal price = variant.getPrice() != null ? variant.getPrice() : BigDecimal.ZERO;

                OrderItem oi = new OrderItem();
                oi.setOrder(order);
                oi.setProductVariant(variant);
                oi.setQuantity(quantity);
                oi.setPriceAtPurchase(price);
                oi.setCostAtPurchase(variant.getCostPrice() != null ? variant.getCostPrice() : BigDecimal.ZERO);
                oi.setEngravingText(engravingText.isEmpty() ? null : engravingText);
                oi.setCustomResize(customResize.isEmpty() ? null : customResize);
                em.persist(oi);

                orderTotalCost = orderTotalCost.add(oi.getCostAtPurchase().multiply(BigDecimal.valueOf(quantity)));

                // Deduct stock for standard orders (non-customized)
                if (engravingText.isEmpty() && customResize.isEmpty()) {
                    Inventory inv = em.find(Inventory.class, variantId);
                    if (inv != null) {
                        int oldQty = inv.getQuantityOnHand();
                        int newQty = Math.max(0, oldQty - quantity);
                        inv.setQuantityOnHand(newQty);
                        em.merge(inv);
                        
                        // Low Stock Notification
                        if (oldQty > inv.getLowStockThreshold() && newQty <= inv.getLowStockThreshold()) {
                            final String variantName = variant.getProduct().getName() + " - " + variant.getMetalColor();
                            final int finalQty = newQty;
                            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                                new org.springframework.transaction.support.TransactionSynchronization() {
                                    @Override
                                    public void afterCommit() {
                                        try {
                                            notificationService.notifyStaffByRole(java.util.List.of("ADMIN", "MANAGER", "STOCK_MANAGER"), "LOW_STOCK", "Low Stock Alert: " + variantName + " is down to " + finalQty + " items.", "/admin.html");
                                        } catch (Exception ignored) {}
                                    }
                                }
                            );
                        }
                    }
                }
            }

            order.setTotalCost(orderTotalCost);
            em.merge(order);

            // Create payment record
            Payment payment = new Payment();
            payment.setOrder(order);
            payment.setPaymentMethod(paymentMethod);
            payment.setAmount(order.getTotalAmount());
            payment.setPaymentStatus(initialPaymentStatus);
            payment.setSlipImageUrl(slipImageUrl);
            String pfx = switch (paymentMethod) {
                case "PAYHERE", "CARD", "STRIPE" -> "TXN-CARD-";
                case "BANK_TRANSFER" -> "TXN-BNK-";
                case "COD" -> "TXN-COD-";
                case "COD_WITH_DEPOSIT", "COD_WITH_BANK_DEPOSIT", "ADVANCE_COD" -> "TXN-ADV-";
                default -> "TXN-";
            };
            payment.setTransactionId(pfx + String.format("%05d", order.getId()));
            em.persist(payment);

            // Clear cart
            em.createQuery("DELETE FROM CartItem c WHERE c.user.id = :uid")
                    .setParameter("uid", user.getId())
                    .executeUpdate();

            // Mock PayHere data
            String merchantId = "1228220";
            String currency = "LKR";
            
            boolean isDeposit = paymentMethod.endsWith("DEPOSIT");
            String amountStr = isDeposit ? "1000.00" : order.getTotalAmount().setScale(2).toString();
            
            String md5Hash = "";
            try {
                String merchantSecret = "MzkwODkzNDE3MTQxOTMzODk2NDQ0MDExNTY3OTA4MzI0NzEzMjQ4Mw==";
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
                md.update(merchantSecret.getBytes());
                String hashedSecret = bytesToHex(md.digest());
                
                String hashString = merchantId + order.getId() + amountStr + currency + hashedSecret;
                md.update(hashString.getBytes());
                md5Hash = bytesToHex(md.digest());
            } catch (Exception ignored) {}

            // For FULL CARD payments, we wait until PayHere succeeds to send the order confirmation.
            // For all other methods (Deposits, COD, Bank Transfer), we send it immediately.
            if (!"CARD".equals(paymentMethod)) {
                final String fUserName = user.getFullName();
                final int fOrderId = order.getId();
                try {
                    org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                        new org.springframework.transaction.support.TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                emailService.sendOrderConfirmation(order);
                                try {
                                    notificationService.notifyAdmins("NEW_ORDER", "New Order #CLC-" + String.format("%05d", fOrderId) + " placed by " + fUserName, "/admin.html");
                                } catch (Exception ignored) {}
                            }
                        }
                    );
                } catch (Exception ignored) {}
            }

            String paymentDetails;
            if ("CARD".equals(paymentMethod)) {
                paymentDetails = "Card Payment (LKR " + order.getTotalAmount() + ")";
            } else if (isDeposit) {
                BigDecimal depAmt = new BigDecimal(amountStr);
                BigDecimal remCod = order.getTotalAmount().subtract(depAmt);
                paymentDetails = paymentMethod + " (Advance Deposit: LKR " + depAmt + ", Remaining COD Balance: LKR " + (remCod.compareTo(BigDecimal.ZERO) > 0 ? remCod : BigDecimal.ZERO) + " payable on delivery)";
            } else if ("COD".equals(paymentMethod)) {
                paymentDetails = "Full COD (LKR " + order.getTotalAmount() + " payable on delivery)";
            } else {
                paymentDetails = paymentMethod + " (LKR " + order.getTotalAmount() + (slipImageUrl != null ? ", Slip Uploaded" : "") + ")";
            }

            auditLogService.log(request, "PLACE_ORDER", "ORDER",
                    "Web Order #CLC-" + String.format("%05d", order.getId()) + " placed by " + user.getEmail() + " | Total: LKR " + order.getTotalAmount() + " | Payment: " + paymentDetails + " [Status: " + order.getPaymentStatus() + "]",
                    "SUCCESS");

            return ResponseEntity.ok("{\"success\":true,\"message\":\"Order placed successfully!\",\"orderId\":" + order.getId() 
                + ",\"total\":" + order.getTotalAmount() 
                + ",\"merchantId\":\"" + merchantId + "\""
                + ",\"currency\":\"" + currency + "\""
                + ",\"amount\":" + amountStr
                + ",\"hash\":\"" + md5Hash + "\""
                + ",\"firstName\":\"" + esc(user.getFullName()) + "\""
                + ",\"lastName\":\"\""
                + ",\"email\":\"" + esc(user.getEmail()) + "\""
                + ",\"phone\":\"" + esc(shippingAddr.getPhone()) + "\""
                + ",\"address\":\"" + esc(shippingAddr.getStreet()) + "\""
                + ",\"city\":\"" + esc(shippingAddr.getCity()) + "\""
                + ",\"country\":\"Sri Lanka\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("{\"success\":false,\"message\":\"Order failed: " + esc(e.getMessage()) + "\"}");
        }
    }

    // ── POST /api/orders/pay-success ──────────────────────────────────────────
    @PostMapping(value = "/pay-success", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> paySuccess(@RequestBody JsonNode body, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        User user = (session != null) ? (User) session.getAttribute("loggedInUser") : null;
        if (user == null) return ResponseEntity.status(401).body("{\"success\":false,\"message\":\"Not authenticated\"}");

        try {
            int orderId = body.path("orderId").asInt();
            Order order = em.find(Order.class, orderId);
            
            if (order == null || !order.getUser().getId().equals(user.getId())) {
                return ResponseEntity.status(404).body("{\"success\":false,\"message\":\"Order not found or unauthorized.\"}");
            }

            String oldPaymentStatus = order.getPaymentStatus();
            
            String paymentMethod = "COD";
            java.util.List<com.ceylonletterco.entity.Payment> pmts = em.createQuery("SELECT p FROM Payment p WHERE p.order = :ord ORDER BY p.id DESC", com.ceylonletterco.entity.Payment.class)
                    .setParameter("ord", order)
                    .setMaxResults(1)
                    .getResultList();
            if (!pmts.isEmpty()) {
                paymentMethod = pmts.get(0).getPaymentMethod();
            }
            
            if ("COD_WITH_DEPOSIT".equals(paymentMethod)) {
                order.setPaymentStatus("PARTIALLY_PAID");
            } else {
                order.setPaymentStatus("PAID");
            }
            order.setOrderStatus("PROCESSING");
            em.merge(order);
            em.flush();
            
            try {
                // If it's a FULL CARD payment, they haven't received an order confirmation yet, so send it now.
                // Otherwise (for deposits), they already got the order confirmation initially, so send a payment status update instead.
                final String finalPaymentMethod = paymentMethod;
                final String fUserName = user.getFullName();
                final int fOrderId = order.getId();
                org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            if ("CARD".equals(finalPaymentMethod)) {
                                emailService.sendOrderConfirmation(order);
                                try {
                                    notificationService.notifyAdmins("NEW_ORDER", "New Order #CLC-" + String.format("%05d", fOrderId) + " placed by " + fUserName, "/admin.html");
                                } catch (Exception ignored) {}
                            } else {
                                emailService.sendPaymentStatusUpdate(order, oldPaymentStatus);
                            }
                        }
                    }
                );
            } catch (Exception ignored) {}

            auditLogService.log(request, "ONLINE_PAYMENT_SUCCESS", "ORDER",
                    "Online payment verified for Order #CLC-" + String.format("%05d", order.getId()) + " (" + order.getPaymentStatus() + ")",
                    "SUCCESS");

            return ResponseEntity.ok("{\"success\":true,\"message\":\"Payment successful.\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── POST / PUT /api/orders/{id}/return-request & /api/orders/{id}/return ──
    @RequestMapping(value = {"/{id}/return-request", "/{id}/return"}, method = {RequestMethod.POST, RequestMethod.PUT}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> requestReturn(@PathVariable int id, @RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        User user = (session != null) ? (User) session.getAttribute("loggedInUser") : null;
        if (user == null) {
            return ResponseEntity.status(401).body("{\"success\":false,\"message\":\"Not authenticated\"}");
        }

        try {
            Order order = em.find(Order.class, id);
            if (order == null || (!order.getUser().getId().equals(user.getId()) && !"ADMIN".equalsIgnoreCase(user.getRole()))) {
                return ResponseEntity.status(404).body("{\"success\":false,\"message\":\"Order not found.\"}");
            }

            String reason = "Customer requested return";
            if (body != null && body.has("reason") && !body.get("reason").asText().isBlank()) {
                reason = body.get("reason").asText().trim();
            }

            order.setOrderStatus("RETURN_REQUESTED");
            order.setReturnReason(reason);
            em.merge(order);

            // Notify staff
            if (notificationService != null) {
                notificationService.notifyStaffByRole(
                    List.of("ADMIN", "MANAGER", "SUPPORT_OFFICER"),
                    "RETURN_REQUESTED",
                    "Return requested for Order #CLC-" + String.format("%05d", id) + ": " + reason,
                    "/admin.html"
                );
            }

            auditLogService.log(request, "REQUEST_RETURN", "ORDER",
                    "Customer " + user.getEmail() + " requested return for Order #CLC-" + String.format("%05d", id) + " (Reason: " + reason + ")",
                    "SUCCESS");

            return ResponseEntity.ok("{\"success\":true,\"message\":\"Return request submitted successfully!\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    private String getPrimaryImage(int productId) {
        List<String> urls = em.createQuery(
                "SELECT i.imageUrl FROM ProductImage i WHERE i.product.id = :pid ORDER BY i.isPrimary DESC, i.id ASC",
                String.class).setParameter("pid", productId).setMaxResults(1).getResultList();
        return urls.isEmpty() ? "" : urls.get(0);
    }

    private String orderToJson(Order o) {
        Long sum = em.createQuery("SELECT SUM(i.quantity) FROM OrderItem i WHERE i.order = :ord", Long.class)
                     .setParameter("ord", o).getSingleResult();
        int itemsCount = (sum != null) ? sum.intValue() : 0;
        
        java.util.List<Payment> pmts = em.createQuery("SELECT p FROM Payment p WHERE p.order = :ord ORDER BY p.id DESC", Payment.class)
            .setParameter("ord", o).getResultList();
        String paymentMethod = pmts.isEmpty() ? "COD" : pmts.get(0).getPaymentMethod();
        
        String recipientName = "";
        String recipientPhone = "";
        String fullShippingAddress = "";
        if (o.getShippingAddress() != null) {
            Address a = o.getShippingAddress();
            recipientName = a.getFullName() != null ? a.getFullName() : "";
            recipientPhone = a.getPhone() != null ? a.getPhone() : "";
            StringBuilder sb = new StringBuilder();
            if (a.getStreet() != null && !a.getStreet().isBlank()) sb.append(a.getStreet());
            if (a.getCity() != null && !a.getCity().isBlank()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(a.getCity());
            }
            if (a.getDistrict() != null && !a.getDistrict().isBlank()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(a.getDistrict());
            }
            if (a.getPostalCode() != null && !a.getPostalCode().isBlank()) {
                sb.append(" (").append(a.getPostalCode()).append(")");
            }
            fullShippingAddress = sb.toString();
        }
        
        boolean hasDeposit = (paymentMethod != null && (paymentMethod.contains("DEPOSIT")
                           || "COD_WITH_BANK_DEPOSIT".equals(paymentMethod) 
                           || "COD_WITH_DEPOSIT".equals(paymentMethod) 
                           || "BANK_TRANSFER_DEPOSIT".equals(paymentMethod)
                           || "CARD_DEPOSIT".equals(paymentMethod)))
                           || (o.getPaymentStatus() != null && o.getPaymentStatus().contains("DEPOSIT"));
        
        java.math.BigDecimal depositAmount = hasDeposit ? new java.math.BigDecimal("1000.00") : java.math.BigDecimal.ZERO;
        java.math.BigDecimal balanceDue = o.getTotalAmount().subtract(depositAmount);
        if (balanceDue.compareTo(java.math.BigDecimal.ZERO) < 0) balanceDue = java.math.BigDecimal.ZERO;
        
        boolean isSettled = "DELIVERED".equalsIgnoreCase(o.getOrderStatus()) 
                         || "PAID".equalsIgnoreCase(o.getPaymentStatus()) 
                         || "SETTLED".equalsIgnoreCase(o.getPaymentStatus());
        
        if (isSettled) {
            balanceDue = java.math.BigDecimal.ZERO;
        }

        String email = o.getUser() != null ? o.getUser().getEmail() : "";
        if (email == null || email.isBlank() || email.contains("@phone.ceylonletterco.com")) {
            if (o.getShippingAddress() != null && o.getShippingAddress().getUser() != null) {
                email = o.getShippingAddress().getUser().getEmail();
            }
        }
        
        boolean hasActiveWarranty = false;
        try {
            List<OrderItem> items = em.createQuery(
                "SELECT oi FROM OrderItem oi JOIN FETCH oi.productVariant v JOIN FETCH v.product p WHERE oi.order.id = :oid",
                OrderItem.class).setParameter("oid", o.getId()).getResultList();
            
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime orderTime = o.getCreatedAt() != null ? o.getCreatedAt() : now;

            for (OrderItem oi : items) {
                if (oi.getProductVariant() != null && oi.getProductVariant().getProduct() != null) {
                    String wp = oi.getProductVariant().getProduct().getWarrantyPeriod();
                    int months = parseWarrantyMonths(wp);
                    if (months > 0) {
                        LocalDateTime expireDate = orderTime.plusMonths(months);
                        if (now.isBefore(expireDate)) {
                            hasActiveWarranty = true;
                            break;
                        }
                    }
                }
            }
        } catch (Exception ex) {
            hasActiveWarranty = false;
        }

        return "{\"id\":" + o.getId()
            + ",\"orderStatus\":\"" + esc(o.getOrderStatus()) + "\""
            + ",\"paymentStatus\":\"" + esc(o.getPaymentStatus()) + "\""
            + ",\"total\":" + o.getTotalAmount()
            + ",\"subtotal\":" + (o.getTotalAmount().subtract(o.getShippingFee()))
            + ",\"shippingFee\":" + o.getShippingFee()
            + ",\"tax\":" + o.getTax()
            + ",\"paymentMethod\":\"" + esc(paymentMethod) + "\""
            + ",\"email\":\"" + esc(email) + "\""
            + ",\"recipientName\":\"" + esc(recipientName) + "\""
            + ",\"recipientPhone\":\"" + esc(recipientPhone) + "\""
            + ",\"shippingAddress\":\"" + esc(fullShippingAddress) + "\""
            + ",\"hasDeposit\":" + hasDeposit
            + ",\"depositAmount\":" + depositAmount
            + ",\"balanceDue\":" + balanceDue
            + ",\"isSettled\":" + isSettled
            + ",\"hasActiveWarranty\":" + hasActiveWarranty
            + ",\"customNotes\":\"" + esc(o.getCustomNotes()) + "\""
            + ",\"itemsCount\":" + itemsCount
            + ",\"date\":\"" + (o.getCreatedAt() != null ? o.getCreatedAt().toLocalDate().toString() : "") + "\""
            + "}";
    }

    private static int parseWarrantyMonths(String wp) {
        if (wp == null || wp.isBlank()) return 0;
        String s = wp.trim().toLowerCase();
        if (s.contains("none") || s.contains("no warranty")) return 0;
        if (s.contains("2 year") || s.contains("2-year") || s.contains("24 month")) return 24;
        if (s.contains("1 year") || s.contains("1-year") || s.contains("12 month")) return 12;
        if (s.contains("3 year") || s.contains("3-year") || s.contains("36 month")) return 36;
        if (s.contains("6 month") || s.contains("6-month")) return 6;
        if (s.contains("3 month") || s.contains("3-month")) return 3;
        if (s.contains("1 month") || s.contains("1-month")) return 1;
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\s*(year|month)").matcher(s);
            if (m.find()) {
                int num = Integer.parseInt(m.group(1));
                String unit = m.group(2);
                return unit.startsWith("year") ? num * 12 : num;
            }
        } catch(Exception e) {}
        return 0;
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}
