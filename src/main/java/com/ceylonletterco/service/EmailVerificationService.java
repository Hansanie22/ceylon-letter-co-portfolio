package com.ceylonletterco.service;

import com.ceylonletterco.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

/**
 * EmailVerificationService – migrated from the original EJB @Stateless service.
 * Sends account verification emails asynchronously using Spring's JavaMailSender.
 *
 * Replaces the JNDI mail session lookup used in the Jakarta EE version.
 */
@Service
public class EmailVerificationService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${app.mail.noreply.from:ceylonletterco@gmail.com}")
    private String fromAddress;

    private void setSafeFrom(MimeMessageHelper helper, String rawFrom) {
        try {
            if (rawFrom != null && rawFrom.contains("<") && rawFrom.contains(">")) {
                String email = rawFrom.substring(rawFrom.indexOf("<") + 1, rawFrom.indexOf(">")).trim();
                String name = rawFrom.substring(0, rawFrom.indexOf("<")).trim();
                if (name.isEmpty()) name = "Ceylon Letter Co.";
                helper.setFrom(email, name);
            } else if (rawFrom != null && !rawFrom.isBlank()) {
                helper.setFrom(rawFrom.trim(), "Ceylon Letter Co.");
            } else {
                helper.setFrom("ceylonletterco@gmail.com", "Ceylon Letter Co.");
            }
        } catch (Exception e) {
            try { helper.setFrom("ceylonletterco@gmail.com"); } catch (Exception ignored) {}
        }
    }

    /**
     * Sends email verification link to the user.
     * Runs asynchronously so it doesn't block the registration request.
     */
    @Async("taskExecutor")
    public void sendVerificationEmail(User user, String appBaseUrl, String returnUrl) {
        try {
            String verifyUrl = appBaseUrl + "/api/auth/verify?token=" + user.getVerificationToken()
                    + (returnUrl != null && !returnUrl.isEmpty() ? "&returnUrl=" + returnUrl : "");

            String plainText = "Hi " + user.getFullName() + ",\n\n"
                    + "Thank you for signing up at Ceylon Letter Co.\n"
                    + "Please confirm your email address by opening the link below:\n\n"
                    + verifyUrl + "\n\n"
                    + "This link expires in 24 hours. If you did not create an account, please ignore this email.\n\n"
                    + "Best regards,\nCeylon Letter Co. Team";

            String html = """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                </head>
                <body style="font-family:'Inter', system-ui, -apple-system, BlinkMacSystemFont, Arial, sans-serif; background:#F8F9FA; padding:40px 20px; margin:0; -webkit-font-smoothing:antialiased;">
                <!-- Anti-Spam Preheader preview text -->
                <div style="display:none;font-size:1px;color:#ffffff;line-height:1px;max-height:0px;max-width:0px;opacity:0;overflow:hidden;">
                  Welcome to Ceylon Letter Co.! Please confirm your email address to complete registration.
                </div>
                <div style="max-width:600px; margin:0 auto; background:#FFFFFF; border:1px solid #EBE4D8; border-radius:8px; overflow:hidden; box-shadow:0 4px 12px rgba(0,0,0,0.03);">
                  <div style="background:#1B1918; padding:36px 20px; text-align:center;">
                    <h1 style="color:#C9A96E; font-family:'Cormorant Garamond', Georgia, serif; font-size:24px; font-weight:600; margin:0; letter-spacing: 3px; text-transform:uppercase;">CEYLON LETTER CO.</h1>
                    <p style="color:#9A9490; font-size:11px; letter-spacing: 2px; margin:6px 0 0 0; text-transform:uppercase;">Account Verification</p>
                  </div>
                  <div style="padding:36px 40px 24px 40px;">
                    <h2 style="color:#2A2622; font-family:'Cormorant Garamond', Georgia, serif; font-size:22px; font-weight:700; margin-top:0;">Confirm Your Email Address</h2>
                    <p style="color:#5A5550; font-size:15px; margin-bottom:18px;">Hi %s,</p>
                    <p style="color:#5A5550; font-size:14px; margin-bottom:28px; line-height:1.6;">
                       Thank you for signing up at <strong style="color:#2A2622;">Ceylon Letter Co.</strong> 
                       Please click the button below to confirm your email address and activate your account.
                    </p>
                    <div style="text-align:center; margin-top:32px; margin-bottom:32px;">
                      <a href="%s" target="_blank" style="display:inline-block; background:#1B1918; color:#C9A96E; text-decoration:none; padding:14px 32px; font-size:14px; font-weight:600; letter-spacing:1px; border-radius:4px; border:1px solid #C9A96E;">Confirm Email Address</a>
                    </div>
                    <p style="color:#7A7470; font-size:13px; margin-bottom:24px; line-height:1.5;">
                       Or copy and paste this link into your browser:<br/>
                       <a href="%s" style="color:#8B734B; word-break:break-all; font-size:12px;">%s</a>
                    </p>
                    <p style="margin-top:32px; font-size:12px; color:#9A9490; text-align:left; border-top:1px solid #F5E6D0; padding-top:20px; line-height:1.5;">
                      This link expires in 24 hours. If you did not create an account with Ceylon Letter Co., you can safely ignore this email.
                    </p>
                  </div>
                  <div style="background:#1B1918; padding:24px 20px; text-align:center;">
                    <p style="color:#9A9490; font-size:12px; margin:0 0 6px 0;">&copy; 2026 Ceylon Letter Co. All rights reserved.</p>
                    <p style="color:#7A7470; font-size:11px; margin:0;">This is an automated transactional message &ndash; please do not reply.</p>
                  </div>
                </div></body></html>
                """.formatted(user.getFullName(), verifyUrl, verifyUrl, verifyUrl);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            setSafeFrom(helper, fromAddress);
            helper.setReplyTo("ceylonletterco@gmail.com", "Ceylon Letter Co. Support");
            helper.setTo(user.getEmail());
            helper.setSubject("Confirm your email address - Ceylon Letter Co.");
            helper.setText(plainText, html);
            
            message.setHeader("Auto-Submitted", "auto-generated");
            message.setHeader("X-Auto-Response-Suppress", "OOF, AutoReply");
            
            mailSender.send(message);

        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(EmailVerificationService.class).error("[EmailVerificationService] Failed to send verification email to " + user.getEmail(), e);
        }
    }

    /**
     * Sends password reset email.
     */
    @Async("taskExecutor")
    public void sendResetEmail(User user, String baseUrl, String token) {
        try {
            String resetUrl = baseUrl + "/reset-password.html?token=" + token;
            
            String plainText = "Hi " + user.getFullName() + ",\n\n"
                    + "We received a request to reset your password for your Ceylon Letter Co. account.\n"
                    + "Please open the link below to set a new password:\n\n"
                    + resetUrl + "\n\n"
                    + "This link expires in 1 hour. If you did not request a password reset, please ignore this email.\n\n"
                    + "Best regards,\nCeylon Letter Co. Team";

            String html = """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                </head>
                <body style="font-family:'Inter', system-ui, -apple-system, BlinkMacSystemFont, Arial, sans-serif; background:#F8F9FA; padding:40px 20px; margin:0; -webkit-font-smoothing:antialiased;">
                <div style="display:none;font-size:1px;color:#ffffff;line-height:1px;max-height:0px;max-width:0px;opacity:0;overflow:hidden;">
                  Reset your Ceylon Letter Co. password.
                </div>
                <div style="max-width:600px; margin:0 auto; background:#FFFFFF; border:1px solid #EBE4D8; border-radius:8px; overflow:hidden; box-shadow:0 4px 12px rgba(0,0,0,0.03);">
                  <div style="background:#1B1918; padding:36px 20px; text-align:center;">
                    <h1 style="color:#C9A96E; font-family:'Cormorant Garamond', Georgia, serif; font-size:24px; font-weight:600; margin:0; letter-spacing: 3px; text-transform:uppercase;">CEYLON LETTER CO.</h1>
                    <p style="color:#9A9490; font-size:11px; letter-spacing: 2px; margin:6px 0 0 0; text-transform:uppercase;">Password Reset</p>
                  </div>
                  <div style="padding:36px 40px 24px 40px;">
                    <h2 style="color:#2A2622; font-family:'Cormorant Garamond', Georgia, serif; font-size:22px; font-weight:700; margin-top:0;">Reset Your Password</h2>
                    <p style="color:#5A5550; font-size:15px; margin-bottom:18px;">Hi %s,</p>
                    <p style="color:#5A5550; font-size:14px; margin-bottom:28px; line-height:1.6;">
                      We received a request to reset your password for your Ceylon Letter Co. account. Click the button below to set a new password:
                    </p>
                    <div style="text-align:center; margin-top:32px; margin-bottom:32px;">
                      <a href="%s" target="_blank" style="display:inline-block; background:#1B1918; color:#C9A96E; text-decoration:none; padding:14px 32px; font-size:14px; font-weight:600; letter-spacing:1px; border-radius:4px; border:1px solid #C9A96E;">Reset Password</a>
                    </div>
                    <p style="margin-top:32px; font-size:12px; color:#9A9490; text-align:left; border-top:1px solid #F5E6D0; padding-top:20px; line-height:1.5;">
                      This link expires in 1 hour. If you did not request a password reset, you can safely ignore this message.
                    </p>
                  </div>
                  <div style="background:#1B1918; padding:24px 20px; text-align:center;">
                    <p style="color:#9A9490; font-size:12px; margin:0 0 6px 0;">&copy; 2026 Ceylon Letter Co. All rights reserved.</p>
                    <p style="color:#7A7470; font-size:11px; margin:0;">This is an automated transactional message &ndash; please do not reply.</p>
                  </div>
                </div></body></html>
                """.formatted(user.getFullName(), resetUrl);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            setSafeFrom(helper, fromAddress);
            helper.setReplyTo("ceylonletterco@gmail.com", "Ceylon Letter Co. Support");
            helper.setTo(user.getEmail());
            helper.setSubject("Reset your password - Ceylon Letter Co.");
            helper.setText(plainText, html);

            message.setHeader("Auto-Submitted", "auto-generated");
            message.setHeader("X-Auto-Response-Suppress", "OOF, AutoReply");

            mailSender.send(message);

        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(EmailVerificationService.class).error("[EmailVerificationService] Failed to send reset email", e);
        }
    }

    @Autowired
    private jakarta.persistence.EntityManager em;

    @Async("taskExecutor")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public void sendOrderConfirmation(com.ceylonletterco.entity.Order order) {
        try {
            // Re-attach order or find it
            order = em.find(com.ceylonletterco.entity.Order.class, order.getId());
            if (order == null) return;

            java.util.List<com.ceylonletterco.entity.OrderItem> items = em.createQuery(
                "SELECT oi FROM OrderItem oi JOIN FETCH oi.productVariant v JOIN FETCH v.product WHERE oi.order.id = :oid", 
                com.ceylonletterco.entity.OrderItem.class)
                .setParameter("oid", order.getId()).getResultList();

            java.util.List<com.ceylonletterco.entity.Payment> pmts = em.createQuery(
                "SELECT p FROM Payment p WHERE p.order = :ord ORDER BY p.id DESC", 
                com.ceylonletterco.entity.Payment.class)
                .setParameter("ord", order).getResultList();
            
            String paymentMethod = pmts.isEmpty() ? "COD" : pmts.get(0).getPaymentMethod();
            
            StringBuilder itemsHtml = new StringBuilder();
            itemsHtml.append("<table style='width:100%; border-collapse: collapse; margin-top: 20px; font-size:14px; color:#2A2622;'>");
            
            java.math.BigDecimal subtotal = java.math.BigDecimal.ZERO;
            for (com.ceylonletterco.entity.OrderItem item : items) {
                java.util.List<String> imgUrls = em.createQuery(
                    "SELECT i.imageUrl FROM ProductImage i WHERE i.product.id = :pid ORDER BY i.isPrimary DESC, i.id ASC", 
                    String.class).setParameter("pid", item.getProductVariant().getProduct().getId()).setMaxResults(1).getResultList();
                String imgUrl = imgUrls.isEmpty() ? "https://ceylonletterco.com/images/favicon.png" : imgUrls.get(0);
                if (imgUrl.startsWith("images/")) {
                    imgUrl = "https://ceylonletterco.com/" + imgUrl;
                }

                String variantDetails = "";
                if (item.getProductVariant().getMetalColor() != null) {
                    variantDetails += "<span style='color:#9A9490; font-size:12px; display:block;'>" + item.getProductVariant().getMetalColor() + "</span>";
                }
                
                if (item.getEngravingText() != null && !item.getEngravingText().trim().isEmpty()) {
                    variantDetails += "<span style='color:#C9A96E; font-size:12px; display:block;'>Engraving: " + item.getEngravingText() + "</span>";
                }
                if (item.getCustomResize() != null && !item.getCustomResize().trim().isEmpty()) {
                    variantDetails += "<span style='color:#C9A96E; font-size:12px; display:block;'>Size: " + item.getCustomResize() + "</span>";
                }
                variantDetails += "<span style='color:#9A9490; font-size:12px; display:block;'>Qty: " + item.getQuantity() + "</span>";

                java.math.BigDecimal itemTotal = item.getPriceAtPurchase().multiply(java.math.BigDecimal.valueOf(item.getQuantity()));
                subtotal = subtotal.add(itemTotal);

                itemsHtml.append("<tr>")
                         .append("<td style='padding:16px 0; border-bottom:1px solid #EBE4D8; width:70px;' valign='top'>")
                         .append("<img src='").append(imgUrl).append("' alt='Product Image' style='width:60px; height:60px; object-fit:cover; border:1px solid #EBE4D8;' />")
                         .append("</td>")
                         .append("<td style='padding:16px 12px; border-bottom:1px solid #EBE4D8;' valign='top'>")
                         .append("<strong style='font-size:15px; display:block; margin-bottom:4px;'>").append(item.getProductVariant().getProduct().getName()).append("</strong>")
                         .append(variantDetails)
                         .append("</td>")
                         .append("<td style='padding:16px 0; border-bottom:1px solid #EBE4D8; text-align:right; font-weight:600;' valign='top'>Rs. ")
                         .append(String.format("%.2f", itemTotal))
                         .append("</td></tr>");
            }
            itemsHtml.append("</table>");
            
            String addressStr = "";
            if (order.getShippingAddress() != null) {
                addressStr = order.getShippingAddress().getStreet() + ", " + order.getShippingAddress().getCity() + " (" + order.getShippingAddress().getPostalCode() + ")";
            }
            
            String paymentStatusStr = "COD_PENDING";
            if (!pmts.isEmpty()) paymentStatusStr = pmts.get(0).getPaymentStatus();
            
            java.math.BigDecimal shippingFee = new java.math.BigDecimal("350.00");
            java.math.BigDecimal tax = java.math.BigDecimal.ZERO;
            
            String friendlyPaymentMethod = paymentMethod.replace("_", " ");

            String html = """
                <html><body style="font-family:'Inter', Arial, sans-serif; background:#F8F9FA; padding:40px 20px; margin:0;">
                <div style="max-width:600px; margin:0 auto; background:#FFFFFF; border:1px solid #EBE4D8;">
                  <!-- HEADER -->
                  <div style="background:#1B1918; padding:40px 20px; text-align:center;">
                    <h1 style="color:#C9A96E; font-family:'Cormorant Garamond', Georgia, serif; font-size:26px; font-weight:600; margin:0; letter-spacing: 4px; text-transform:uppercase;">CEYLON LETTER CO.</h1>
                    <p style="color:#9A9490; font-size:11px; letter-spacing: 2px; margin:8px 0 0 0; text-transform:uppercase;">ORDER CONFIRMATION</p>
                  </div>
                  
                  <div style="padding:40px 40px 20px 40px;">
                    <h2 style="color:#2A2622; font-family:'Cormorant Garamond', Georgia, serif; font-size:20px; font-weight:700; margin-top:0;">ORDER PLACED SUCCESSFULLY!</h2>
                    <p style="color:#5A5550; font-size:14px; margin-bottom:20px;">Hi %s,</p>
                    <p style="color:#5A5550; font-size:14px; margin-bottom:30px; line-height:1.6;">
                      Thank you for shopping at Ceylon Letter Co.. Your order has been registered and is being processed. Here are your order details:
                    </p>
                    
                    <!-- SUMMARY BOX -->
                    <div style="background:#FDF8F0; padding:24px; border:1px solid #F0E5D3; margin-bottom:30px;">
                      <table style="width:100%%; font-size:13px; color:#5A5550; line-height:1.8;">
                        <tr><td style="width:130px; padding-bottom:8px;">Order Number:</td><td style="color:#2A2622; font-weight:600; padding-bottom:8px;">#CLC-%05d</td></tr>
                        <tr><td style="padding-bottom:8px;">Order Status:</td><td style="color:#2A2622; font-weight:600; padding-bottom:8px;">%s</td></tr>
                        <tr><td style="padding-bottom:8px;">Payment Method:</td><td style="color:#2A2622; font-weight:600; padding-bottom:8px;">%s</td></tr>
                        <tr><td style="padding-bottom:8px;">Payment Status:</td><td style="color:#2A2622; font-weight:600; padding-bottom:8px;">%s</td></tr>
                        <tr><td valign="top" style="padding-bottom:8px;">Shipping Address:</td><td style="color:#2A2622; font-weight:600; padding-bottom:8px;">%s</td></tr>
                      </table>
                    </div>
                    
                    <!-- ITEMS -->
                    %s
                    
                    <!-- TOTALS -->
                    <table style="width:100%%; font-size:14px; color:#5A5550; margin-top:20px; border-collapse:collapse;">
                      <tr>
                        <td style="padding:8px 0;">Subtotal</td>
                        <td style="text-align:right; padding:8px 0; font-weight:600;">Rs. %.2f</td>
                      </tr>
                      <tr>
                        <td style="padding:8px 0; border-bottom:1px solid #EBE4D8;">Shipping Fee</td>
                        <td style="text-align:right; padding:8px 0; border-bottom:1px solid #EBE4D8; font-weight:600;">Rs. %.2f</td>
                      </tr>
                      <tr>
                        <td style="padding:16px 0; font-weight:700; color:#2A2622; font-size:16px;">Total Amount</td>
                        <td style="text-align:right; padding:16px 0; font-weight:700; color:#2A2622; font-size:16px;">Rs. %.2f</td>
                      </tr>
                    </table>
                    
                    <div style="text-align:center; margin-top:40px; margin-bottom:20px;">
                      <a href="https://ceylonletterco.com/account.html" style="display:inline-block; background:#1B1918; color:#C9A96E; text-decoration:none; padding:12px 24px; font-size:14px; font-weight:600; letter-spacing:1px; border-radius:4px;">View Your Order</a>
                    </div>
                  </div>
                  
                  <!-- FOOTER -->
                  <div style="background:#1B1918; padding:30px 20px; text-align:center;">
                    <p style="color:#9A9490; font-size:12px; margin:0 0 8px 0;">&copy; 2026 Ceylon Letter Co.. All rights reserved.</p>
                    <p style="color:#7A7470; font-size:11px; margin:0;">This is an automated message &ndash; please do not reply.</p>
                  </div>
                </div>
                </body></html>
                """.formatted(
                    order.getUser().getFullName(), 
                    order.getId(),
                    order.getOrderStatus(),
                    friendlyPaymentMethod,
                    paymentStatusStr,
                    addressStr,
                    itemsHtml.toString(),
                    subtotal,
                    shippingFee,
                    order.getTotalAmount()
                );

            String targetEmail = order.getUser() != null ? order.getUser().getEmail() : null;
            if (targetEmail == null || targetEmail.isBlank() || targetEmail.contains("@phone.ceylonletterco.com")) {
                org.slf4j.LoggerFactory.getLogger(EmailVerificationService.class).info("[EmailVerificationService] Skipped order confirmation email for order #" + order.getId() + " - User has no email registered.");
                return;
            }

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            setSafeFrom(helper, fromAddress);
            helper.setReplyTo("ceylonletterco@gmail.com", "Ceylon Letter Co. Support");
            helper.setTo(targetEmail);
            helper.setSubject("Your Ceylon Letter Co Order #CLC-" + String.format("%05d", order.getId()));
            helper.setText(html, true);
            
            message.setHeader("Auto-Submitted", "auto-generated");
            message.setHeader("X-Auto-Response-Suppress", "OOF, AutoReply");

            mailSender.send(message);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(EmailVerificationService.class).error("[EmailVerificationService] Failed to send order confirmation email", e);
        }
    }

    @Async("taskExecutor")
    public void sendOrderStatusUpdate(com.ceylonletterco.entity.Order order, String oldStatus) {
        try {
            String html = """
                <html><body style="font-family:'Inter', Arial, sans-serif; background:#F8F9FA; padding:40px 20px; margin:0;">
                <div style="max-width:600px; margin:0 auto; background:#FFFFFF; border:1px solid #EBE4D8;">
                  <div style="background:#1B1918; padding:40px 20px; text-align:center;">
                    <h1 style="color:#C9A96E; font-family:'Cormorant Garamond', Georgia, serif; font-size:26px; font-weight:600; margin:0; letter-spacing: 4px; text-transform:uppercase;">CEYLON LETTER CO.</h1>
                    <p style="color:#9A9490; font-size:11px; letter-spacing: 2px; margin:8px 0 0 0; text-transform:uppercase;">ORDER UPDATE</p>
                  </div>
                  <div style="padding:40px 40px 20px 40px;">
                    <h2 style="color:#2A2622; font-family:'Cormorant Garamond', Georgia, serif; font-size:20px; font-weight:700; margin-top:0;">ORDER STATUS UPDATED</h2>
                    <p style="color:#5A5550; font-size:14px; margin-bottom:20px;">Hi %s,</p>
                    <p style="color:#5A5550; font-size:14px; line-height:1.6; margin-bottom:30px;">
                      Your order <strong style="color:#2A2622;">#CLC-%05d</strong> status has been updated from 
                      <span style="color:#9A9490; text-decoration:line-through;">%s</span> to 
                      <strong style="color:#C9A96E; font-size:16px;">%s</strong>.
                    </p>
                    <div style="text-align:center; margin-top:40px; margin-bottom:20px;">
                      <a href="https://ceylonletterco.com/account.html" style="display:inline-block; background:#1B1918; color:#C9A96E; text-decoration:none; padding:12px 24px; font-size:14px; font-weight:600; letter-spacing:1px; border-radius:4px;">VIEW YOUR ORDER</a>
                    </div>
                  </div>
                  <div style="background:#1B1918; padding:30px 20px; text-align:center;">
                    <p style="color:#9A9490; font-size:12px; margin:0 0 8px 0;">&copy; 2026 Ceylon Letter Co.. All rights reserved.</p>
                    <p style="color:#7A7470; font-size:11px; margin:0;">This is an automated message &ndash; please do not reply.</p>
                  </div>
                </div></body></html>
                """.formatted(order.getUser().getFullName(), order.getId(), oldStatus, order.getOrderStatus());

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            setSafeFrom(helper, fromAddress);
            helper.setReplyTo("ceylonletterco@gmail.com", "Ceylon Letter Co. Support");
            helper.setTo(order.getUser().getEmail());
            helper.setSubject("Order Status Update #CLC-" + String.format("%05d", order.getId()));
            helper.setText(html, true);

            message.setHeader("Auto-Submitted", "auto-generated");
            message.setHeader("X-Auto-Response-Suppress", "OOF, AutoReply");

            mailSender.send(message);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(EmailVerificationService.class).error("Failed to send order status email", e);
        }
    }

    @Async("taskExecutor")
    public void sendPaymentStatusUpdate(com.ceylonletterco.entity.Order order, String oldStatus) {
        try {
            String title = "Payment Status Updated";
            String messageHtml = "";
            String newStatus = order.getPaymentStatus();
            
            if ("PENDING_VERIFICATION".equals(oldStatus)) {
                if ("PAID".equals(newStatus) || "DEPOSIT_PAID".equals(newStatus)) {
                    title = "Payment Successfully Verified";
                    messageHtml = """
                        <p style="color:#5A5550; font-size:14px; line-height:1.6; margin-bottom:20px;">
                          We are pleased to inform you that your bank transfer for order <strong style="color:#2A2622;">#CLC-%05d</strong> has been successfully verified. 
                        </p>
                        <p style="color:#5A5550; font-size:14px; line-height:1.6; margin-bottom:30px;">
                          We will now begin processing your order and will notify you once it has been dispatched.
                        </p>
                    """.formatted(order.getId());
                } else if ("REJECTED".equals(newStatus)) {
                    title = "Payment Verification Failed";
                    messageHtml = """
                        <p style="color:#5A5550; font-size:14px; line-height:1.6; margin-bottom:20px;">
                          Unfortunately, we were unable to verify the bank transfer slip you uploaded for order <strong style="color:#2A2622;">#CLC-%05d</strong>. 
                        </p>
                        <p style="color:#5A5550; font-size:14px; line-height:1.6; margin-bottom:30px;">
                          This may happen if the slip image was unclear or the transaction details did not match. Please log in to your account and upload a clearer slip, or contact our support team for assistance.
                        </p>
                    """.formatted(order.getId());
                }
            }
            
            if (messageHtml.isEmpty()) {
                messageHtml = """
                  <p style="color:#5A5550; font-size:14px; line-height:1.6; margin-bottom:30px;">
                    The payment status for your order <strong style="color:#2A2622;">#CLC-%05d</strong> has been updated from 
                    <span style="color:#9A9490; text-decoration:line-through;">%s</span> to 
                    <strong style="color:#C9A96E; font-size:16px;">%s</strong>.
                  </p>
                """.formatted(order.getId(), oldStatus, newStatus);
            }

            String html = """
                <html><body style="font-family:'Inter', Arial, sans-serif; background:#F8F9FA; padding:40px 20px; margin:0;">
                <div style="max-width:600px; margin:0 auto; background:#FFFFFF; border:1px solid #EBE4D8;">
                  <div style="background:#1B1918; padding:40px 20px; text-align:center;">
                    <h1 style="color:#C9A96E; font-family:'Cormorant Garamond', Georgia, serif; font-size:26px; font-weight:600; margin:0; letter-spacing: 4px; text-transform:uppercase;">CEYLON LETTER CO.</h1>
                    <p style="color:#9A9490; font-size:11px; letter-spacing: 2px; margin:8px 0 0 0; text-transform:uppercase;">PAYMENT UPDATE</p>
                  </div>
                  <div style="padding:40px 40px 20px 40px;">
                    <h2 style="color:#2A2622; font-family:'Cormorant Garamond', Georgia, serif; font-size:20px; font-weight:700; margin-top:0; text-transform:uppercase;">%s</h2>
                    <p style="color:#5A5550; font-size:14px; margin-bottom:20px;">Hi %s,</p>
                    %s
                    <div style="text-align:center; margin-top:40px; margin-bottom:20px;">
                      <a href="https://ceylonletterco.com/account.html" style="display:inline-block; background:#1B1918; color:#C9A96E; text-decoration:none; padding:12px 24px; font-size:14px; font-weight:600; letter-spacing:1px; border-radius:4px;">VIEW YOUR ORDER</a>
                    </div>
                  </div>
                  <div style="background:#1B1918; padding:30px 20px; text-align:center;">
                    <p style="color:#9A9490; font-size:12px; margin:0 0 8px 0;">&copy; 2026 Ceylon Letter Co.. All rights reserved.</p>
                    <p style="color:#7A7470; font-size:11px; margin:0;">This is an automated message &ndash; please do not reply.</p>
                  </div>
                </div></body></html>
                """.formatted(title, order.getUser().getFullName(), messageHtml);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            setSafeFrom(helper, fromAddress);
            helper.setReplyTo("ceylonletterco@gmail.com", "Ceylon Letter Co. Support");
            helper.setTo(order.getUser().getEmail());
            helper.setSubject(title + " - #CLC-" + String.format("%05d", order.getId()));
            helper.setText(html, true);

            message.setHeader("Auto-Submitted", "auto-generated");
            message.setHeader("X-Auto-Response-Suppress", "OOF, AutoReply");

            mailSender.send(message);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(EmailVerificationService.class).error("Failed to send payment status email", e);
        }
    }

    @Async("taskExecutor")
    public void sendSubscriptionWelcome(String email) {
        try {
            String html = """
                <html><body style="font-family:'Inter', Arial, sans-serif; background:#F8F9FA; padding:40px 20px; margin:0;">
                <div style="max-width:600px; margin:0 auto; background:#FFFFFF; border:1px solid #EBE4D8;">
                  <div style="background:#1B1918; padding:40px 20px; text-align:center;">
                    <h1 style="color:#C9A96E; font-family:'Cormorant Garamond', Georgia, serif; font-size:26px; font-weight:600; margin:0; letter-spacing: 4px; text-transform:uppercase;">CEYLON LETTER CO.</h1>
                    <p style="color:#9A9490; font-size:11px; letter-spacing: 2px; margin:8px 0 0 0; text-transform:uppercase;">NEWSLETTER</p>
                  </div>
                  <div style="padding:40px 40px 20px 40px;">
                    <h2 style="color:#2A2622; font-family:'Cormorant Garamond', Georgia, serif; font-size:20px; font-weight:700; margin-top:0;">WELCOME TO CEYLON LETTER CO.</h2>
                    <p style="color:#5A5550; font-size:14px; margin-bottom:20px;">Hi there,</p>
                    <p style="color:#5A5550; font-size:14px; line-height:1.6; margin-bottom:30px;">
                      Thank you for subscribing to our newsletter! You'll now be the first to know about our latest collections, exclusive offers, and special discounts.
                    </p>
                    <div style="text-align:center; margin-top:40px; margin-bottom:20px;">
                      <a href="https://ceylonletterco.com/store.html" style="display:inline-block; background:#1B1918; color:#C9A96E; text-decoration:none; padding:12px 24px; font-size:14px; font-weight:600; letter-spacing:1px; border-radius:4px;">SHOP NOW</a>
                    </div>
                  </div>
                  <div style="background:#1B1918; padding:30px 20px; text-align:center;">
                    <p style="color:#9A9490; font-size:12px; margin:0 0 8px 0;">&copy; 2026 Ceylon Letter Co.. All rights reserved.</p>
                    <p style="color:#7A7470; font-size:11px; margin:0;">If you wish to unsubscribe, you can do so at any time on our website.</p>
                  </div>
                </div></body></html>
                """;

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            setSafeFrom(helper, fromAddress);
            helper.setReplyTo("ceylonletterco@gmail.com", "Ceylon Letter Co. Support");
            helper.setTo(email);
            helper.setSubject("Welcome to Ceylon Letter Co. Exclusive Discounts!");
            helper.setText(html, true);

            message.setHeader("Auto-Submitted", "auto-generated");
            message.setHeader("X-Auto-Response-Suppress", "OOF, AutoReply");

            mailSender.send(message);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(EmailVerificationService.class).error("Failed to send subscription welcome email", e);
        }
    }

    @Async("taskExecutor")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public void sendNewProductBroadcast(com.ceylonletterco.entity.Product product, String imgUrl) {
        broadcastToSubscribers("New Arrival: " + product.getName(), "We're excited to introduce a brand new addition to our collection!", product, imgUrl);
    }

    @Async("taskExecutor")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public void sendRestockBroadcast(com.ceylonletterco.entity.Product product, com.ceylonletterco.entity.ProductVariant variant, String imgUrl) {
        String subtitle = "Great news! The " + product.getName() + (variant.getMetalColor() != null ? " (" + variant.getMetalColor() + ")" : "") + " is back in stock.";
        broadcastToSubscribers("Back in Stock: " + product.getName(), subtitle, product, imgUrl);
    }

    @Async("taskExecutor")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public void sendDiscountBroadcast(com.ceylonletterco.entity.Product product, String imgUrl) {
        broadcastToSubscribers("Special Discount on " + product.getName(), "Don't miss out on this exclusive offer. Shop now while stocks last!", product, imgUrl);
    }

    private void broadcastToSubscribers(String title, String subtitle, com.ceylonletterco.entity.Product product, String imgUrl) {
        try {
            java.util.List<String> emails = em.createNativeQuery("SELECT email FROM discount_subscribers").getResultList();
            if (emails.isEmpty()) return;

            if (imgUrl != null && imgUrl.startsWith("images/")) {
                imgUrl = "https://ceylonletterco.com/" + imgUrl;
            } else if (imgUrl == null) {
                imgUrl = "https://ceylonletterco.com/images/favicon.png";
            }

            String html = """
                <html><body style="font-family:'Inter', Arial, sans-serif; background:#F8F9FA; padding:40px 20px; margin:0;">
                <div style="max-width:600px; margin:0 auto; background:#FFFFFF; border:1px solid #EBE4D8;">
                  <div style="background:#1B1918; padding:40px 20px; text-align:center;">
                    <h1 style="color:#C9A96E; font-family:'Cormorant Garamond', Georgia, serif; font-size:26px; font-weight:600; margin:0; letter-spacing: 4px; text-transform:uppercase;">CEYLON LETTER CO.</h1>
                    <p style="color:#9A9490; font-size:11px; letter-spacing: 2px; margin:8px 0 0 0; text-transform:uppercase;">ANNOUNCEMENT</p>
                  </div>
                  <div style="padding:40px 40px 20px 40px;">
                    <h2 style="color:#2A2622; font-family:'Cormorant Garamond', Georgia, serif; font-size:20px; font-weight:700; margin-top:0;">%s</h2>
                    <p style="color:#5A5550; font-size:14px; line-height:1.6; margin-bottom:30px;">
                      %s
                    </p>
                    <div style="text-align:center;">
                      <img src="%s" alt="Product Image" style="width:100%%; max-width:400px; height:auto; border-radius:8px; margin-bottom:30px; border:1px solid #EBE4D8;" />
                    </div>
                    <div style="text-align:center; margin-top:10px; margin-bottom:20px;">
                      <a href="https://ceylonletterco.com/product-view.html?id=%d" style="display:inline-block; background:#1B1918; color:#C9A96E; text-decoration:none; padding:12px 24px; font-size:14px; font-weight:600; letter-spacing:1px; border-radius:4px;">SHOP NOW</a>
                    </div>
                  </div>
                  <div style="background:#1B1918; padding:30px 20px; text-align:center;">
                    <p style="color:#9A9490; font-size:12px; margin:0 0 8px 0;">&copy; 2026 Ceylon Letter Co.. All rights reserved.</p>
                    <p style="color:#7A7470; font-size:11px; margin:0;">You are receiving this because you subscribed to Ceylon Letter Co. exclusive discounts.</p>
                  </div>
                </div></body></html>
                """.formatted(title, subtitle, imgUrl, product.getId());

            for (String email : emails) {
                try {
                    MimeMessage message = mailSender.createMimeMessage();
                    MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                    setSafeFrom(helper, fromAddress);
                    helper.setReplyTo("ceylonletterco@gmail.com", "Ceylon Letter Co. Support");
                    helper.setTo(email);
                    helper.setSubject(title);
                    helper.setText(html, true);

                    message.setHeader("Auto-Submitted", "auto-generated");
                    message.setHeader("X-Auto-Response-Suppress", "OOF, AutoReply");

                    mailSender.send(message);
                } catch (Exception ex) {
                    org.slf4j.LoggerFactory.getLogger(EmailVerificationService.class).error("Failed to send broadcast to " + email, ex);
                }
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(EmailVerificationService.class).error("Failed to process broadcast", e);
        }
    }
}
