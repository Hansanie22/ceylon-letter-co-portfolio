package com.auracraft.service;

import com.auracraft.entity.*;
import jakarta.mail.internet.MimeMessage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;

/**
 * OrderReportScheduler
 *
 * 1. Runs at 11:00 PM: sends each Sales Rep their personal daily order report (email + Google Sheets cumulative update)
 * 2. Runs at 11:59 PM: sends Admins the full business daily Excel report
 */
@Service
public class OrderReportScheduler {

    private static final Logger LOG = Logger.getLogger(OrderReportScheduler.class.getName());

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private JavaMailSender mailSender;

    @Value("${app.mail.noreply.from:admin@auracraft.com}")
    private String fromEmail;


    private static final String[] ADMIN_EMAILS = {
            "neeradalakvindu@gmail.com",
            "veolakvindu@gmail.com",
            "hansanieprabodha@gmail.com"
    };

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ── 11:00 PM: Auto-transition all non-cancelled PENDING orders to PROCESSING ──
    @Scheduled(cron = "0 0 23 * * ?")
    @Transactional
    public void autoTransitionPendingToProcessing() {
        LOG.info("Running 11:00 PM auto-transition of PENDING orders to PROCESSING...");
        try {
            int updatedOrders = em.createQuery(
                "UPDATE Order o SET o.orderStatus = 'PROCESSING' WHERE o.orderStatus = 'PENDING'")
                .executeUpdate();
            int updatedPosOrders = em.createQuery(
                "UPDATE PosOrder po SET po.orderStatus = 'PROCESSING' WHERE po.orderStatus = 'PENDING'")
                .executeUpdate();
            LOG.info(String.format("Auto-transitioned %d website orders and %d POS orders to PROCESSING.", updatedOrders, updatedPosOrders));
        } catch (Exception e) {
            LOG.severe("Failed auto-transitioning PENDING orders: " + e.getMessage());
        }
    }

    // ── 11:00 PM: Send each Sales Rep their personal order report ─────────────
    @Scheduled(cron = "0 0 23 * * ?")
    @Transactional(readOnly = true)
    public void sendSalesRepDailyReports() {
        LOG.info("Starting Sales Rep daily reports...");
        try {
            LocalDate today = LocalDate.now();
            LocalDateTime start = today.atStartOfDay();
            LocalDateTime end = today.atTime(23, 59, 59);

            // Get all staff users (exclude CUSTOMER)
            List<User> staffUsers = em.createQuery(
                    "SELECT u FROM User u WHERE u.role != 'CUSTOMER' AND u.isActive = true", User.class)
                    .getResultList();

            for (User staff : staffUsers) {
                if (staff.getEmail() == null || staff.getEmail().isBlank()) continue;

                List<PosOrder> myOrders = em.createQuery(
                        "SELECT o FROM PosOrder o WHERE o.salesRep.id = :uid AND o.createdAt >= :start AND o.createdAt <= :end ORDER BY o.createdAt ASC",
                        PosOrder.class)
                        .setParameter("uid", staff.getId())
                        .setParameter("start", start)
                        .setParameter("end", end)
                        .getResultList();

                if (myOrders.isEmpty()) {
                    LOG.info("No orders today for " + staff.getFullName() + " — skipping email.");
                    continue;
                }

                // Build & send styled Excel for sales rep
                byte[] excel = buildSalesRepExcel(myOrders, staff.getFullName(), today);
                sendSalesRepEmail(staff.getEmail(), staff.getFullName(), excel, today, myOrders.size());


            }
        } catch (Exception e) {
            LOG.severe("Sales Rep daily report failed: " + e.getMessage());
        }
    }

    // ── 11:59 PM: Send Admin full business daily report ───────────────────────
    @Scheduled(cron = "0 59 23 * * ?")
    @Transactional(readOnly = true)
    public void generateAndSendDailyReport() {
        LOG.info("Starting Admin Daily Order Report Generation...");
        try {
            LocalDate today = LocalDate.now();
            LocalDateTime start = today.atStartOfDay();
            LocalDateTime end = today.atTime(23, 59, 59);

            List<Order> webOrders = em.createQuery(
                    "SELECT o FROM Order o WHERE o.createdAt >= :start AND o.createdAt <= :end ORDER BY o.createdAt ASC", Order.class)
                    .setParameter("start", start).setParameter("end", end).getResultList();

            List<PosOrder> posOrders = em.createQuery(
                    "SELECT o FROM PosOrder o WHERE o.createdAt >= :start AND o.createdAt <= :end ORDER BY o.createdAt ASC", PosOrder.class)
                    .setParameter("start", start).setParameter("end", end).getResultList();

            byte[] excelFile = buildAdminExcel(webOrders, posOrders, today);
            sendAdminEmail(excelFile, today);
            LOG.info("Admin Daily Order Report successfully sent.");
        } catch (Exception e) {
            LOG.severe("Admin daily report failed: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EXCEL BUILDERS
    // ─────────────────────────────────────────────────────────────────────────

    private byte[] buildSalesRepExcel(List<PosOrder> orders, String repName, LocalDate date) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = wb.createSheet("My Orders - " + date);

            // Header style — gold background, bold, white text, font 14
            XSSFCellStyle headerStyle = wb.createCellStyle();
            XSSFFont headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 14);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte)146, (byte)64, (byte)14}, null)); // #92400e
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.MEDIUM);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            // Total row style — light yellow background, bold
            XSSFCellStyle totalStyle = wb.createCellStyle();
            XSSFFont totalFont = wb.createFont();
            totalFont.setBold(true);
            totalFont.setFontHeightInPoints((short) 13);
            totalStyle.setFont(totalFont);
            totalStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte)254, (byte)243, (byte)199}, null)); // #fef3c7
            totalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Data row style — alternating
            XSSFCellStyle dataStyle = wb.createCellStyle();
            XSSFFont dataFont = wb.createFont();
            dataFont.setFontHeightInPoints((short) 12);
            dataStyle.setFont(dataFont);
            dataStyle.setBorderBottom(BorderStyle.THIN);

            String[] headers = {
                    "Order_ID", "Date", "Customer_Name", "Address", "Phone1", "Phone2",
                    "Subtotal", "Discount", "Delivery_Fee", "Total_Bill", "Payment_Method", "Advance_Paid", "COD_Balance",
                    "Order_Status", "Custom", "Notes"
            };

            // Title row
            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Daily Sales Report — " + repName + " — " + date);
            XSSFCellStyle titleStyle = wb.createCellStyle();
            XSSFFont titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);
            titleStyle.setFont(titleFont);
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, headers.length - 1));

            // Header row
            Row headerRow = sheet.createRow(1);
            for (int i = 0; i < headers.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
            }
            headerRow.setHeightInPoints(24);

            BigDecimal sumTotal = BigDecimal.ZERO;
            BigDecimal sumAdvance = BigDecimal.ZERO;
            BigDecimal sumCod = BigDecimal.ZERO;
            int rowIdx = 2;

            for (PosOrder o : orders) {
                List<PosOrderItem> items = em.createQuery(
                        "SELECT oi FROM PosOrderItem oi JOIN FETCH oi.productVariant v JOIN FETCH v.product WHERE oi.posOrder.id = :oid",
                        PosOrderItem.class).setParameter("oid", o.getId()).getResultList();

                StringBuilder itemsStr = new StringBuilder();
                for (PosOrderItem oi : items) {
                    itemsStr.append(oi.getProductVariant().getProduct().getName())
                            .append(" x").append(oi.getQuantity());
                    if (oi.getEngravingText() != null && !oi.getEngravingText().isBlank())
                        itemsStr.append("[Eng:").append(oi.getEngravingText()).append("]");
                    itemsStr.append(" | ");
                }

                String notes = (o.getCustomNotes() != null ? o.getCustomNotes() : "") + " " + itemsStr;

                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue("POS-" + String.format("%05d", o.getId()));
                row.createCell(1).setCellValue(o.getCreatedAt() != null ? o.getCreatedAt().format(DTF) : "");
                row.createCell(2).setCellValue(o.getCustomerName() != null ? o.getCustomerName() : "");
                row.createCell(3).setCellValue(o.getCustomerAddress() != null ? o.getCustomerAddress() : "");
                row.createCell(4).setCellValue(o.getPhone1() != null ? o.getPhone1() : "");
                row.createCell(5).setCellValue(o.getPhone2() != null ? o.getPhone2() : "");
                row.createCell(6).setCellValue(o.getSubtotal() != null ? o.getSubtotal().doubleValue() : 0);
                row.createCell(7).setCellValue(o.getDiscountAmount() != null ? o.getDiscountAmount().doubleValue() : 0);
                row.createCell(8).setCellValue(o.getDeliveryCharge() != null ? o.getDeliveryCharge().doubleValue() : 0);
                row.createCell(9).setCellValue(o.getTotalAmount().doubleValue());
                row.createCell(10).setCellValue(o.getPaymentMethod());
                row.createCell(11).setCellValue(o.getAdvancePaid().doubleValue());
                row.createCell(12).setCellValue(o.getCodBalance().doubleValue());
                row.createCell(13).setCellValue(o.getOrderStatus());
                row.createCell(14).setCellValue(Boolean.TRUE.equals(o.getIsCustom()) ? "YES" : "NO");
                row.createCell(15).setCellValue(notes.trim());

                for (int c = 0; c < headers.length; c++) row.getCell(c).setCellStyle(dataStyle);

                sumTotal = sumTotal.add(o.getTotalAmount());
                sumAdvance = sumAdvance.add(o.getAdvancePaid());
                sumCod = sumCod.add(o.getCodBalance());
            }

            // Totals row
            Row totalRow = sheet.createRow(rowIdx);
            totalRow.createCell(0).setCellValue("TOTAL (" + orders.size() + " orders)");
            totalRow.createCell(6).setCellValue(sumTotal.doubleValue());
            totalRow.createCell(8).setCellValue(sumAdvance.doubleValue());
            totalRow.createCell(9).setCellValue(sumCod.doubleValue());
            for (int c = 0; c < headers.length; c++) {
                Cell cell = totalRow.getCell(c);
                if (cell == null) cell = totalRow.createCell(c);
                cell.setCellStyle(totalStyle);
            }

            // Auto-size
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            sheet.setColumnWidth(3, 8000); // address wider

            wb.write(out);
            return out.toByteArray();
        }
    }

    private byte[] buildAdminExcel(List<Order> webOrders, List<PosOrder> posOrders, LocalDate date) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = wb.createSheet("Daily Orders");

            XSSFCellStyle headerStyle = wb.createCellStyle();
            XSSFFont headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 13);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte)30, (byte)64, (byte)175}, null));
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            XSSFCellStyle totalStyle = wb.createCellStyle();
            XSSFFont totalFont = wb.createFont();
            totalFont.setBold(true);
            totalFont.setFontHeightInPoints((short) 13);
            totalStyle.setFont(totalFont);
            totalStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte)219, (byte)234, (byte)254}, null));
            totalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            String[] headers = {
                    "Order_ID", "Source", "Date", "Customer_Name", "Address", "Phone",
                    "Sales_Rep", "Total_Bill", "Payment_Method", "Advance_Paid", "COD_Balance",
                    "Order_Status", "Custom", "Notes"
            };

            Row hRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = hRow.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
            }
            hRow.setHeightInPoints(22);

            BigDecimal sumTotal = BigDecimal.ZERO;
            BigDecimal sumAdvance = BigDecimal.ZERO;
            BigDecimal sumCod = BigDecimal.ZERO;
            int rowIdx = 1;

            // Website orders
            for (Order o : webOrders) {
                List<Payment> payments = em.createQuery("SELECT p FROM Payment p WHERE p.order.id = :oid ORDER BY p.id DESC", Payment.class)
                        .setParameter("oid", o.getId()).setMaxResults(1).getResultList();
                Payment pmt = payments.isEmpty() ? null : payments.get(0);
                List<OrderItem> items = em.createQuery(
                        "SELECT oi FROM OrderItem oi JOIN FETCH oi.productVariant v JOIN FETCH v.product WHERE oi.order.id = :oid",
                        OrderItem.class).setParameter("oid", o.getId()).getResultList();

                String customerName = o.getShippingAddress() != null ? o.getShippingAddress().getFullName() : (o.getUser() != null ? o.getUser().getFullName() : "");
                String address = o.getShippingAddress() != null ? o.getShippingAddress().getStreet() + ", " + o.getShippingAddress().getCity() : "";
                String phone = o.getShippingAddress() != null ? o.getShippingAddress().getPhone() : (o.getUser() != null ? o.getUser().getPhone() : "");
                String payMethod = pmt != null ? pmt.getPaymentMethod() : "N/A";
                BigDecimal advance = BigDecimal.ZERO;
                BigDecimal cod = BigDecimal.ZERO;
                if ("COD_WITH_DEPOSIT".equals(payMethod) && pmt != null) {
                    advance = pmt.getAmount();
                    cod = o.getTotalAmount().subtract(advance);
                } else if ("COD".equals(payMethod)) {
                    cod = o.getTotalAmount();
                } else {
                    advance = o.getTotalAmount();
                }

                StringBuilder itemsStr = new StringBuilder();
                for (OrderItem oi : items) {
                    itemsStr.append(oi.getProductVariant().getProduct().getName()).append(" x").append(oi.getQuantity()).append(" | ");
                }

                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue("CLC-" + String.format("%05d", o.getId()));
                row.createCell(1).setCellValue("Website");
                row.createCell(2).setCellValue(o.getCreatedAt() != null ? o.getCreatedAt().format(DTF) : "");
                row.createCell(3).setCellValue(customerName);
                row.createCell(4).setCellValue(address);
                row.createCell(5).setCellValue(phone);
                row.createCell(6).setCellValue("Website");
                row.createCell(7).setCellValue(o.getTotalAmount().doubleValue());
                row.createCell(8).setCellValue(payMethod);
                row.createCell(9).setCellValue(advance.doubleValue());
                row.createCell(10).setCellValue(cod.doubleValue());
                row.createCell(11).setCellValue(o.getOrderStatus());
                row.createCell(12).setCellValue("NO");
                row.createCell(13).setCellValue(itemsStr.toString());

                sumTotal = sumTotal.add(o.getTotalAmount());
                sumAdvance = sumAdvance.add(advance);
                sumCod = sumCod.add(cod);
            }

            // POS orders
            for (PosOrder o : posOrders) {
                List<PosOrderItem> items = em.createQuery(
                        "SELECT oi FROM PosOrderItem oi JOIN FETCH oi.productVariant v JOIN FETCH v.product WHERE oi.posOrder.id = :oid",
                        PosOrderItem.class).setParameter("oid", o.getId()).getResultList();

                StringBuilder itemsStr = new StringBuilder();
                if (o.getCustomNotes() != null && !o.getCustomNotes().isBlank()) itemsStr.append("[").append(o.getCustomNotes()).append("] ");
                for (PosOrderItem oi : items) {
                    itemsStr.append(oi.getProductVariant().getProduct().getName()).append(" x").append(oi.getQuantity()).append(" | ");
                }

                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue("POS-" + String.format("%05d", o.getId()));
                row.createCell(1).setCellValue("POS/WhatsApp");
                row.createCell(2).setCellValue(o.getCreatedAt() != null ? o.getCreatedAt().format(DTF) : "");
                row.createCell(3).setCellValue(o.getCustomerName() != null ? o.getCustomerName() : "");
                row.createCell(4).setCellValue(o.getCustomerAddress() != null ? o.getCustomerAddress() : "");
                row.createCell(5).setCellValue(o.getPhone1() != null ? o.getPhone1() : "");
                row.createCell(6).setCellValue(o.getSalesRep().getFullName());
                row.createCell(7).setCellValue(o.getTotalAmount().doubleValue());
                row.createCell(8).setCellValue(o.getPaymentMethod());
                row.createCell(9).setCellValue(o.getAdvancePaid().doubleValue());
                row.createCell(10).setCellValue(o.getCodBalance().doubleValue());
                row.createCell(11).setCellValue(o.getOrderStatus());
                row.createCell(12).setCellValue(Boolean.TRUE.equals(o.getIsCustom()) ? "YES" : "NO");
                row.createCell(13).setCellValue(itemsStr.toString().trim());

                sumTotal = sumTotal.add(o.getTotalAmount());
                sumAdvance = sumAdvance.add(o.getAdvancePaid());
                sumCod = sumCod.add(o.getCodBalance());
            }

            // Totals
            int total = webOrders.size() + posOrders.size();
            Row totalRow = sheet.createRow(rowIdx);
            totalRow.createCell(0).setCellValue("TOTAL (" + total + " orders)");
            totalRow.createCell(7).setCellValue(sumTotal.doubleValue());
            totalRow.createCell(9).setCellValue(sumAdvance.doubleValue());
            totalRow.createCell(10).setCellValue(sumCod.doubleValue());
            for (int c = 0; c < headers.length; c++) {
                Cell cell = totalRow.getCell(c);
                if (cell == null) cell = totalRow.createCell(c);
                cell.setCellStyle(totalStyle);
            }

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            sheet.setColumnWidth(4, 8000);

            wb.write(out);
            return out.toByteArray();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EMAIL SENDERS
    // ─────────────────────────────────────────────────────────────────────────

    private void setSafeFrom(MimeMessageHelper helper, String rawFrom) {
        try {
            if (rawFrom != null && rawFrom.contains("<") && rawFrom.contains(">")) {
                String email = rawFrom.substring(rawFrom.indexOf("<") + 1, rawFrom.indexOf(">")).trim();
                String name = rawFrom.substring(0, rawFrom.indexOf("<")).trim();
                if (name.isEmpty()) name = "AuraCraft Studio";
                helper.setFrom(email, name);
            } else if (rawFrom != null && !rawFrom.isBlank()) {
                helper.setFrom(rawFrom.trim(), "AuraCraft Studio");
            } else {
                helper.setFrom("admin@auracraft.com", "AuraCraft Studio");
            }
        } catch (Exception e) {
            try { helper.setFrom("admin@auracraft.com"); } catch (Exception ignored) {}
        }
    }

    private void sendSalesRepEmail(String toEmail, String repName, byte[] fileData, LocalDate date, int orderCount) throws Exception {
        MimeMessage msg = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(msg, true);
        setSafeFrom(helper, fromEmail);
        helper.setTo(toEmail);
        helper.setSubject("Your Daily Sales Report — " + date + " | AuraCraft Studio");
        helper.setText(
                "<div style='font-family:sans-serif;color:#1c1c1e;'>" +
                "<h2 style='color:#92400e;'>Hi " + repName + "! 👋</h2>" +
                "<p>Your sales report for <strong>" + date + "</strong> is ready.</p>" +
                "<p style='font-size:18px;'><strong>Total Orders Today: " + orderCount + "</strong></p>" +
                "<p>Please find your detailed order list attached as an Excel sheet.</p>" +
                "<p style='color:#6b7280;font-size:12px;'>This is an automated daily report from AuraCraft Studio POS System.</p>" +
                "</div>", true);

        helper.addAttachment("My_Sales_" + date + ".xlsx", new ByteArrayResource(fileData));
        mailSender.send(msg);
        LOG.info("Sales Rep report sent to: " + toEmail);
    }

    private void sendAdminEmail(byte[] fileData, LocalDate date) throws Exception {
        MimeMessage msg = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(msg, true);
        setSafeFrom(helper, fromEmail);
        helper.setTo(ADMIN_EMAILS);
        helper.setSubject("Daily Business Report — " + date + " | AuraCraft Studio");
        helper.setText(
                "<div style='font-family:sans-serif;color:#1c1c1e;'>" +
                "<h2 style='color:#1e40af;'>Daily Business Report</h2>" +
                "<p>Hello Admin Team,</p>" +
                "<p>Attached is the comprehensive daily sales report for <strong>" + date + "</strong>.</p>" +
                "<p>This includes all website orders and POS/WhatsApp orders for today.</p>" +
                "<br><p style='color:#6b7280;'>Regards,<br>AuraCraft Studio System</p>" +
                "</div>", true);

        helper.addAttachment("Daily_Sales_" + date + ".xlsx", new ByteArrayResource(fileData));
        mailSender.send(msg);
    }

}
