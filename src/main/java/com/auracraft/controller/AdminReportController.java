package com.auracraft.controller;

import com.auracraft.entity.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/admin/reports")
public class AdminReportController {

    @PersistenceContext
    private EntityManager em;

    private boolean isAdminOrStaff(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("loggedInUser") == null) return false;
        String role = (String) session.getAttribute("userRole");
        return role.contains("ADMIN") || role.contains("MANAGER") || role.contains("STOCK_MANAGER") || role.contains("SUPPORT_OFFICER") || role.contains("SALES_REP");
    }

    private User getLoggedInUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return null;
        return (User) session.getAttribute("loggedInUser");
    }

    private LocalDateTime parseStart(String start, String period) {
        if (start != null && !start.isBlank()) {
            return LocalDate.parse(start).atStartOfDay();
        }
        if ("weekly".equals(period)) return LocalDateTime.now().minusDays(6).withHour(0).withMinute(0).withSecond(0);
        if ("monthly".equals(period)) return LocalDateTime.now().withDayOfYear(1).withHour(0).withMinute(0).withSecond(0);
        if ("lifetime".equals(period) || "all".equals(period)) return LocalDateTime.of(2020, 1, 1, 0, 0, 0);
        // Default 'daily' = 30 days
        return LocalDateTime.now().minusDays(29).withHour(0).withMinute(0).withSecond(0);
    }

    private LocalDateTime parseEnd(String end) {
        if (end != null && !end.isBlank()) {
            return LocalDate.parse(end).atTime(23, 59, 59);
        }
        return LocalDateTime.now();
    }

    private XSSFCellStyle createDataStyle(XSSFWorkbook wb, XSSFFont font, byte[] rgbColor) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(rgbColor, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        short borderColor = IndexedColors.GREY_50_PERCENT.getIndex();
        style.setTopBorderColor(borderColor);
        style.setBottomBorderColor(borderColor);
        style.setLeftBorderColor(borderColor);
        style.setRightBorderColor(borderColor);
        return style;
    }

    private void createCell(Row row, int colIndex, String val, XSSFCellStyle style) {
        Cell cell = row.createCell(colIndex);
        cell.setCellValue(val != null ? val : "");
        cell.setCellStyle(style);
    }

    private int createProfessionalBannerHeader(XSSFSheet sheet, XSSFWorkbook workbook, String reportTitle, String downloadedByName, int totalColumns) {
        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(44);

        XSSFFont titleFont = workbook.createFont();
        titleFont.setFontName("Calibri");
        titleFont.setFontHeightInPoints((short) 20);
        titleFont.setBold(true);
        titleFont.setColor(new XSSFColor(new byte[]{(byte) 201, (byte) 169, (byte) 110}, null));

        XSSFCellStyle titleStyle = workbook.createCellStyle();
        titleStyle.setFont(titleFont);
        titleStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 26, (byte) 24, (byte) 22}, null));
        titleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        titleStyle.setAlignment(HorizontalAlignment.CENTER);
        titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);

        for (int c = 0; c < totalColumns; c++) {
            Cell cell = titleRow.createCell(c);
            if (c == 0) cell.setCellValue("AURACRAFT STUDIO — OFFICIAL BUSINESS REPORT");
            cell.setCellStyle(titleStyle);
        }
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, totalColumns - 1));

        XSSFFont subFont = workbook.createFont();
        subFont.setFontName("Calibri");
        subFont.setFontHeightInPoints((short) 12);
        subFont.setBold(true);
        subFont.setColor(new XSSFColor(new byte[]{(byte) 254, (byte) 249, (byte) 243}, null));

        XSSFCellStyle subStyle = workbook.createCellStyle();
        subStyle.setFont(subFont);
        subStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 42, (byte) 38, (byte) 34}, null));
        subStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        subStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        subStyle.setAlignment(HorizontalAlignment.LEFT);

        Row row1 = sheet.createRow(1);
        row1.setHeightInPoints(22);
        for (int c = 0; c < totalColumns; c++) {
            Cell cell = row1.createCell(c);
            if (c == 0) cell.setCellValue("  Report Title: " + reportTitle);
            cell.setCellStyle(subStyle);
        }
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, totalColumns - 1));

        Row row2 = sheet.createRow(2);
        row2.setHeightInPoints(22);
        String generationTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String metaText = "  Report Generated On: " + generationTime + "   |   Downloaded By: " + (downloadedByName != null ? downloadedByName : "Administrator / Authorized Staff");
        for (int c = 0; c < totalColumns; c++) {
            Cell cell = row2.createCell(c);
            if (c == 0) cell.setCellValue(metaText);
            cell.setCellStyle(subStyle);
        }
        sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, totalColumns - 1));

        Row blankRow = sheet.createRow(3);
        blankRow.setHeightInPoints(12);

        return 4;
    }

    private void writeWorkbookToResponse(XSSFWorkbook workbook, String filenamePrefix, HttpServletResponse response) throws IOException {
        String filename = filenamePrefix + "_" + LocalDate.now() + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        response.getOutputStream().write(out.toByteArray());
        response.getOutputStream().flush();
    }

    // ── 1. Sales Ledger Excel Report ──────────────────────────────────────────
    @GetMapping({"/sales.excel", "/sales.xlsx", "/sales-ledger.excel"})
    public void generateSalesLedgerReport(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(required = false) String period,
            HttpServletRequest request, HttpServletResponse response) throws IOException {

        if (!isAdminOrStaff(request)) { response.sendError(403, "Access denied."); return; }
        User loggedInUser = getLoggedInUser(request);
        String downloadedByName = loggedInUser != null ? loggedInUser.getFullName() + " (" + loggedInUser.getEmail() + ")" : "Administrator";

        LocalDateTime s = parseStart(start, period);
        LocalDateTime e = parseEnd(end);

        List<Object[]> webRows = em.createQuery(
                "SELECT o.createdAt, o.createdAt, o.id, COALESCE(u.fullName, 'Guest'), " +
                "COALESCE((SELECT p.paymentMethod FROM Payment p WHERE p.order.id = o.id ORDER BY p.id DESC), 'COD'), " +
                "COALESCE((SELECT p.slipImageUrl FROM Payment p WHERE p.order.id = o.id ORDER BY p.id DESC), ''), " +
                "o.totalAmount, COALESCE(o.paymentStatus, 'PENDING'), 'WEB', o.orderStatus, " +
                "COALESCE((SELECT p.transactionId FROM Payment p WHERE p.order.id = o.id ORDER BY p.id DESC), '') " +
                "FROM Order o LEFT JOIN o.user u WHERE o.createdAt BETWEEN :s AND :e ORDER BY o.createdAt DESC",
                Object[].class).setParameter("s", s).setParameter("e", e).getResultList();

        List<Object[]> posRows = em.createQuery(
                "SELECT po.createdAt, po.createdAt, po.id, COALESCE(po.customerName, 'Walk-in Customer'), " +
                "po.paymentMethod, COALESCE(po.paymentSlipUrl, ''), po.totalAmount, " +
                "po.orderStatus, 'POS', po.advancePaid, po.codBalance, '' " +
                "FROM PosOrder po WHERE po.createdAt BETWEEN :s AND :e ORDER BY po.createdAt DESC",
                Object[].class).setParameter("s", s).setParameter("e", e).getResultList();

        List<Object[]> rows = new ArrayList<>();
        rows.addAll(webRows);
        rows.addAll(posRows);
        rows.sort((a, b) -> {
            LocalDateTime dtA = (LocalDateTime) a[0];
            LocalDateTime dtB = (LocalDateTime) b[0];
            if (dtA == null && dtB == null) return 0;
            if (dtA == null) return 1;
            if (dtB == null) return -1;
            return dtB.compareTo(dtA);
        });

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Sales Ledger");
            String[] headers = {"Payment Date", "Order Date", "Order ID", "Customer Name", "Payment Method", "Transaction ID", "Slip Attachment", "Amount (LKR)", "Status"};
            int startRowIndex = createProfessionalBannerHeader(sheet, workbook, "Sales & Revenue Ledger Report", downloadedByName, headers.length);

            XSSFFont headerFont = workbook.createFont();
            headerFont.setFontName("Calibri");
            headerFont.setFontHeightInPoints((short) 16);
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            XSSFCellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 30, (byte) 41, (byte) 59}, null));
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            XSSFFont dataFont = workbook.createFont();
            dataFont.setFontName("Calibri");
            dataFont.setFontHeightInPoints((short) 14);

            XSSFCellStyle yellowStyle = createDataStyle(workbook, dataFont, new byte[]{(byte) 254, (byte) 249, (byte) 195});
            XSSFCellStyle greenStyle  = createDataStyle(workbook, dataFont, new byte[]{(byte) 220, (byte) 252, (byte) 231});
            XSSFCellStyle redStyle    = createDataStyle(workbook, dataFont, new byte[]{(byte) 254, (byte) 226, (byte) 226});
            XSSFCellStyle purpleStyle = createDataStyle(workbook, dataFont, new byte[]{(byte) 243, (byte) 232, (byte) 255});
            XSSFCellStyle defaultStyle = createDataStyle(workbook, dataFont, new byte[]{(byte) 255, (byte) 255, (byte) 255});

            Row headerRow = sheet.createRow(startRowIndex);
            headerRow.setHeightInPoints(28);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            BigDecimal totalSales = BigDecimal.ZERO;
            int rowIndex = startRowIndex + 1;

            for (Object[] r : rows) {
                Row row = sheet.createRow(rowIndex++);
                row.setHeightInPoints(24);

                String payDate = r[0] != null ? ((LocalDateTime) r[0]).format(dtf) : "";
                String ordDate = r[1] != null ? ((LocalDateTime) r[1]).format(dtf) : "";
                boolean isPos  = "POS".equals(r[8]);
                Integer rawId  = (Integer) r[2];
                String ordId   = (isPos ? "POS-" : "CLC-") + String.format("%05d", rawId);
                String cust    = r[3] != null ? r[3].toString() : "Guest";
                String method  = r[4] != null ? r[4].toString() : "";
                String rawSlip = r[5] != null ? r[5].toString().trim() : "";
                String rawTx   = r.length > 10 && r[10] != null ? r[10].toString().trim() : "";

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
                String slipAttachment = (rawSlip.startsWith("http") || rawSlip.contains("cloudinary")) ? rawSlip : "—";
                BigDecimal amt = r[6] != null ? (BigDecimal) r[6] : BigDecimal.ZERO;

                String status = "PENDING";
                if (isPos) {
                    String oStatus = r[7] != null ? r[7].toString() : "PENDING";
                    BigDecimal adv = r[9] != null ? (BigDecimal) r[9] : BigDecimal.ZERO;
                    BigDecimal cod = r.length > 10 && r[10] instanceof BigDecimal ? (BigDecimal) r[10] : BigDecimal.ZERO;
                    if ("CANCELLED".equals(oStatus)) status = "CANCELLED";
                    else if ("RETURNED".equals(oStatus)) status = "RETURNED";
                    else if ("DELIVERED".equals(oStatus) || "COMPLETED".equals(oStatus)) status = "PAID (DELIVERED)";
                    else if ("FULL_PAID".equals(method)) status = "PAID";
                    else if (adv.compareTo(BigDecimal.ZERO) > 0) status = "ADVANCE PAID (COD: LKR " + cod + ")";
                    else status = "COD PENDING";
                } else {
                    String pStatus = r[7] != null ? r[7].toString() : "PENDING";
                    String oStatus = r[9] != null ? r[9].toString() : "PENDING";
                    if ("CANCELLED".equals(oStatus)) status = "CANCELLED";
                    else if ("RETURNED".equals(oStatus)) status = "RETURNED";
                    else if ("PAID".equals(pStatus)) status = "PAID";
                    else if ("DELIVERED".equals(oStatus)) status = "PAID (COD SETTLED)";
                    else status = "PENDING";
                }

                totalSales = totalSales.add(amt);
                XSSFCellStyle rowStyle = defaultStyle;
                if (status.contains("PENDING") || status.contains("UNPAID")) rowStyle = yellowStyle;
                else if (status.contains("PAID") || status.contains("SUCCESS") || status.contains("COMPLETED") || status.contains("DELIVERED") || status.contains("SHIPPED")) rowStyle = greenStyle;
                else if (status.contains("CANCEL")) rowStyle = redStyle;
                else if (status.contains("RETURN")) rowStyle = purpleStyle;

                createCell(row, 0, payDate, rowStyle);
                createCell(row, 1, ordDate, rowStyle);
                createCell(row, 2, ordId, rowStyle);
                createCell(row, 3, cust, rowStyle);
                createCell(row, 4, method, rowStyle);
                createCell(row, 5, txId, rowStyle);
                createCell(row, 6, slipAttachment, rowStyle);
                createCell(row, 7, String.format("LKR %,.2f", amt), rowStyle);
                createCell(row, 8, status, rowStyle);
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, Math.max(sheet.getColumnWidth(i) + 2500, 4800));
            }
            writeWorkbookToResponse(workbook, "Sales_Ledger_Report", response);
        }
    }

    // ── 2. Inventory / Material Stock Report ──────────────────────────────────
    @GetMapping({"/inventory.excel", "/inventory.xlsx", "/inventory-stock.excel", "/material-stock.excel"})
    public void generateInventoryReport(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(required = false) String period,
            HttpServletRequest request, HttpServletResponse response) throws IOException {

        if (!isAdminOrStaff(request)) { response.sendError(403, "Access denied."); return; }
        User loggedInUser = getLoggedInUser(request);
        String downloadedByName = loggedInUser != null ? loggedInUser.getFullName() + " (" + loggedInUser.getEmail() + ")" : "Administrator";

        List<Inventory> list = em.createQuery(
                "SELECT i FROM Inventory i JOIN FETCH i.productVariant pv JOIN FETCH pv.product p ORDER BY p.name ASC", Inventory.class).getResultList();

        List<PackingMaterial> matList = em.createQuery("SELECT pm FROM PackingMaterial pm ORDER BY pm.name ASC", PackingMaterial.class).getResultList();

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Product Stock");
            String[] headers = {"Product Name", "Size / Length", "Metal Color", "SKU", "Qty On Hand", "Low Threshold", "Unit Price (LKR)", "Stock Status"};
            int startRowIndex = createProfessionalBannerHeader(sheet, workbook, "Stock & Inventory Level Report", downloadedByName, headers.length);

            XSSFFont headerFont = workbook.createFont();
            headerFont.setFontName("Calibri");
            headerFont.setFontHeightInPoints((short) 16);
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            XSSFCellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 30, (byte) 41, (byte) 59}, null));
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            XSSFFont dataFont = workbook.createFont();
            dataFont.setFontName("Calibri");
            dataFont.setFontHeightInPoints((short) 14);

            XSSFCellStyle yellowStyle = createDataStyle(workbook, dataFont, new byte[]{(byte) 254, (byte) 249, (byte) 195});
            XSSFCellStyle greenStyle  = createDataStyle(workbook, dataFont, new byte[]{(byte) 220, (byte) 252, (byte) 231});
            XSSFCellStyle redStyle    = createDataStyle(workbook, dataFont, new byte[]{(byte) 254, (byte) 226, (byte) 226});

            Row headerRow = sheet.createRow(startRowIndex);
            headerRow.setHeightInPoints(28);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = startRowIndex + 1;
            for (Inventory i : list) {
                Row row = sheet.createRow(rowIndex++);
                row.setHeightInPoints(24);

                int qty = i.getQuantityOnHand() != null ? i.getQuantityOnHand() : 0;
                int threshold = i.getLowStockThreshold() != null ? i.getLowStockThreshold() : 5;
                BigDecimal price = i.getProductVariant().getPrice() != null ? i.getProductVariant().getPrice() : BigDecimal.ZERO;

                String stockStatus = "In Stock";
                XSSFCellStyle rowStyle = greenStyle;
                if (qty <= 0) { stockStatus = "Out of Stock"; rowStyle = redStyle; }
                else if (qty <= threshold) { stockStatus = "Low Stock Warning"; rowStyle = yellowStyle; }

                createCell(row, 0, i.getProductVariant().getProduct().getName(), rowStyle);
                createCell(row, 1, i.getProductVariant().getSizeLength(), rowStyle);
                createCell(row, 2, i.getProductVariant().getMetalColor(), rowStyle);
                createCell(row, 3, i.getProductVariant().getSkuVariant(), rowStyle);
                createCell(row, 4, String.valueOf(qty), rowStyle);
                createCell(row, 5, String.valueOf(threshold), rowStyle);
                createCell(row, 6, String.format("LKR %,.2f", price), rowStyle);
                createCell(row, 7, stockStatus, rowStyle);
            }

            // Sheet 2: Raw Packing Materials Stock
            XSSFSheet sheet2 = workbook.createSheet("Packing Materials Stock");
            String[] headers2 = {"Material Name", "Quantity In Stock", "Low Threshold", "Unit Cost (LKR)", "Stock Status"};
            int startRowIndex2 = createProfessionalBannerHeader(sheet2, workbook, "Raw Packing Materials Inventory Report", downloadedByName, headers2.length);

            Row headerRow2 = sheet2.createRow(startRowIndex2);
            headerRow2.setHeightInPoints(28);
            for (int i = 0; i < headers2.length; i++) {
                Cell cell = headerRow2.createCell(i);
                cell.setCellValue(headers2[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIndex2 = startRowIndex2 + 1;
            for (PackingMaterial pm : matList) {
                Row row = sheet2.createRow(rowIndex2++);
                row.setHeightInPoints(24);

                int qty = pm.getQtyInStock() != null ? pm.getQtyInStock() : 0;
                int threshold = pm.getLowStockThreshold() != null ? pm.getLowStockThreshold() : 10;
                BigDecimal cost = pm.getUnitCost() != null ? pm.getUnitCost() : BigDecimal.ZERO;

                String stockStatus = "In Stock";
                XSSFCellStyle rowStyle = greenStyle;
                if (qty <= 0) { stockStatus = "Out of Stock"; rowStyle = redStyle; }
                else if (qty <= threshold) { stockStatus = "Low Stock Warning"; rowStyle = yellowStyle; }

                createCell(row, 0, pm.getName(), rowStyle);
                createCell(row, 1, String.valueOf(qty), rowStyle);
                createCell(row, 2, String.valueOf(threshold), rowStyle);
                createCell(row, 3, String.format("LKR %,.2f", cost), rowStyle);
                createCell(row, 4, stockStatus, rowStyle);
            }

            for (int i = 0; i < headers.length; i++) { sheet.autoSizeColumn(i); sheet.setColumnWidth(i, Math.max(sheet.getColumnWidth(i) + 2500, 4800)); }
            for (int i = 0; i < headers2.length; i++) { sheet2.autoSizeColumn(i); sheet2.setColumnWidth(i, Math.max(sheet2.getColumnWidth(i) + 2500, 4800)); }
            writeWorkbookToResponse(workbook, "Inventory_Stock_Report", response);
        }
    }

    // ── 3. Users / Customers Excel Report ─────────────────────────────────────
    @GetMapping({"/users.excel", "/users.xlsx", "/customer.excel"})
    public void generateUsersReport(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(required = false) String period,
            HttpServletRequest request, HttpServletResponse response) throws IOException {

        if (!isAdminOrStaff(request)) { response.sendError(403, "Access denied."); return; }
        User loggedInUser = getLoggedInUser(request);
        String downloadedByName = loggedInUser != null ? loggedInUser.getFullName() + " (" + loggedInUser.getEmail() + ")" : "Administrator";

        LocalDateTime s = parseStart(start, period);
        LocalDateTime e = parseEnd(end);

        List<User> users = em.createQuery(
                "SELECT u FROM User u WHERE u.createdAt BETWEEN :s AND :e ORDER BY u.createdAt DESC", User.class)
                .setParameter("s", s).setParameter("e", e).getResultList();

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Registered Users");
            String[] headers = {"Registration Date", "Full Name", "Email Address", "Phone Number", "Role", "Subscribed", "Account Status"};
            int startRowIndex = createProfessionalBannerHeader(sheet, workbook, "Registered Customers & Users Report", downloadedByName, headers.length);

            XSSFFont headerFont = workbook.createFont();
            headerFont.setFontName("Calibri");
            headerFont.setFontHeightInPoints((short) 16);
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            XSSFCellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 30, (byte) 41, (byte) 59}, null));
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            XSSFFont dataFont = workbook.createFont();
            dataFont.setFontName("Calibri");
            dataFont.setFontHeightInPoints((short) 14);

            XSSFCellStyle greenStyle  = createDataStyle(workbook, dataFont, new byte[]{(byte) 220, (byte) 252, (byte) 231});
            XSSFCellStyle redStyle    = createDataStyle(workbook, dataFont, new byte[]{(byte) 254, (byte) 226, (byte) 226});
            XSSFCellStyle purpleStyle = createDataStyle(workbook, dataFont, new byte[]{(byte) 243, (byte) 232, (byte) 255});

            Row headerRow = sheet.createRow(startRowIndex);
            headerRow.setHeightInPoints(28);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            int rowIndex = startRowIndex + 1;

            for (User u : users) {
                Row row = sheet.createRow(rowIndex++);
                row.setHeightInPoints(24);

                String regDate = u.getCreatedAt() != null ? u.getCreatedAt().format(dtf) : "";
                String role = u.getRole() != null ? u.getRole() : "CUSTOMER";
                boolean active = u.isActive();

                XSSFCellStyle rowStyle = greenStyle;
                if (!active) rowStyle = redStyle;
                else if (role.contains("ADMIN") || role.contains("MANAGER") || role.contains("STAFF")) rowStyle = purpleStyle;

                createCell(row, 0, regDate, rowStyle);
                createCell(row, 1, u.getFullName(), rowStyle);
                createCell(row, 2, u.getEmail(), rowStyle);
                createCell(row, 3, u.getPhone(), rowStyle);
                createCell(row, 4, role, rowStyle);
                createCell(row, 5, u.isSubscribed() ? "Yes" : "No", rowStyle);
                createCell(row, 6, active ? "Active" : "Disabled", rowStyle);
            }

            for (int i = 0; i < headers.length; i++) { sheet.autoSizeColumn(i); sheet.setColumnWidth(i, Math.max(sheet.getColumnWidth(i) + 2500, 4800)); }
            writeWorkbookToResponse(workbook, "Users_Report", response);
        }
    }

    // ── 4. Material Usage Report ──────────────────────────────────────────────
    @GetMapping({"/material-usage.excel", "/material-usage.xlsx"})
    public void generateMaterialUsageReport(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(required = false) String period,
            HttpServletRequest request, HttpServletResponse response) throws IOException {

        if (!isAdminOrStaff(request)) { response.sendError(403, "Access denied."); return; }
        User loggedInUser = getLoggedInUser(request);
        String downloadedByName = loggedInUser != null ? loggedInUser.getFullName() + " (" + loggedInUser.getEmail() + ")" : "Administrator";

        LocalDateTime s = parseStart(start, period);
        LocalDateTime e = parseEnd(end);

        List<PackingMaterialLog> logs = em.createQuery(
                "SELECT log FROM PackingMaterialLog log JOIN FETCH log.packingMaterial pm LEFT JOIN FETCH log.createdBy u " +
                "WHERE log.createdAt BETWEEN :s AND :e ORDER BY log.createdAt DESC", PackingMaterialLog.class)
                .setParameter("s", s).setParameter("e", e).getResultList();

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Material Consumption");
            String[] headers = {"Date & Time", "Material Name", "Action Type", "Quantity Change", "Reason / Notes", "Log By"};
            int startRowIndex = createProfessionalBannerHeader(sheet, workbook, "Packing Material Consumption & Stock Logs", downloadedByName, headers.length);

            XSSFFont headerFont = workbook.createFont();
            headerFont.setFontName("Calibri");
            headerFont.setFontHeightInPoints((short) 16);
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            XSSFCellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 30, (byte) 41, (byte) 59}, null));
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            XSSFFont dataFont = workbook.createFont();
            dataFont.setFontName("Calibri");
            dataFont.setFontHeightInPoints((short) 14);

            XSSFCellStyle defaultStyle = createDataStyle(workbook, dataFont, new byte[]{(byte) 255, (byte) 255, (byte) 255});
            XSSFCellStyle redStyle     = createDataStyle(workbook, dataFont, new byte[]{(byte) 254, (byte) 226, (byte) 226});
            XSSFCellStyle greenStyle   = createDataStyle(workbook, dataFont, new byte[]{(byte) 220, (byte) 252, (byte) 231});

            Row headerRow = sheet.createRow(startRowIndex);
            headerRow.setHeightInPoints(28);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            int rowIndex = startRowIndex + 1;

            for (PackingMaterialLog log : logs) {
                Row row = sheet.createRow(rowIndex++);
                row.setHeightInPoints(24);

                String dt = log.getCreatedAt() != null ? log.getCreatedAt().format(dtf) : "";
                String mat = log.getPackingMaterial() != null ? log.getPackingMaterial().getName() : "Unknown";
                String action = log.getAction() != null ? log.getAction() : "";
                int qtyChange = log.getQuantityChange() != null ? log.getQuantityChange() : 0;
                String reason = log.getReason() != null ? log.getReason() : "";
                String userStr = log.getCreatedBy() != null ? log.getCreatedBy().getFullName() : "System / Staff";

                XSSFCellStyle rowStyle = qtyChange < 0 ? redStyle : (qtyChange > 0 ? greenStyle : defaultStyle);

                createCell(row, 0, dt, rowStyle);
                createCell(row, 1, mat, rowStyle);
                createCell(row, 2, action, rowStyle);
                createCell(row, 3, (qtyChange > 0 ? "+" : "") + qtyChange, rowStyle);
                createCell(row, 4, reason, rowStyle);
                createCell(row, 5, userStr, rowStyle);
            }

            for (int i = 0; i < headers.length; i++) { sheet.autoSizeColumn(i); sheet.setColumnWidth(i, Math.max(sheet.getColumnWidth(i) + 2500, 4800)); }
            writeWorkbookToResponse(workbook, "Material_Usage_Report", response);
        }
    }

    // ── 5. Product Variant Report ─────────────────────────────────────────────
    @GetMapping({"/product.excel", "/product.xlsx"})
    public void generateProductReport(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!isAdminOrStaff(request)) { response.sendError(403, "Access denied."); return; }
        User loggedInUser = getLoggedInUser(request);
        String downloadedByName = loggedInUser != null ? loggedInUser.getFullName() + " (" + loggedInUser.getEmail() + ")" : "Administrator";

        List<ProductVariant> variants = em.createQuery(
                "SELECT pv FROM ProductVariant pv JOIN FETCH pv.product p LEFT JOIN FETCH p.category c ORDER BY p.name ASC", ProductVariant.class).getResultList();

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Product Catalog");
            String[] headers = {"Product Name", "Category", "SKU Variant", "Metal Color", "Size / Length", "Price (LKR)", "Compare Price", "Cost Price"};
            int startRowIndex = createProfessionalBannerHeader(sheet, workbook, "Complete Product Variant & Pricing Catalog Report", downloadedByName, headers.length);

            XSSFFont headerFont = workbook.createFont();
            headerFont.setFontName("Calibri");
            headerFont.setFontHeightInPoints((short) 16);
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            XSSFCellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 30, (byte) 41, (byte) 59}, null));
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            XSSFFont dataFont = workbook.createFont();
            dataFont.setFontName("Calibri");
            dataFont.setFontHeightInPoints((short) 14);
            XSSFCellStyle defaultStyle = createDataStyle(workbook, dataFont, new byte[]{(byte) 255, (byte) 255, (byte) 255});

            Row headerRow = sheet.createRow(startRowIndex);
            headerRow.setHeightInPoints(28);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = startRowIndex + 1;
            for (ProductVariant pv : variants) {
                Row row = sheet.createRow(rowIndex++);
                row.setHeightInPoints(24);

                String pName = pv.getProduct() != null ? pv.getProduct().getName() : "";
                String cat = (pv.getProduct() != null && pv.getProduct().getCategory() != null) ? pv.getProduct().getCategory().getName() : "Uncategorized";
                String sku = pv.getSkuVariant() != null ? pv.getSkuVariant() : "";
                String color = pv.getMetalColor() != null ? pv.getMetalColor() : "";
                String size = pv.getSizeLength() != null ? pv.getSizeLength() : "";
                BigDecimal price = pv.getPrice() != null ? pv.getPrice() : BigDecimal.ZERO;
                BigDecimal comparePrice = pv.getCompareAtPrice() != null ? pv.getCompareAtPrice() : BigDecimal.ZERO;
                BigDecimal costPrice = pv.getCostPrice() != null ? pv.getCostPrice() : BigDecimal.ZERO;

                createCell(row, 0, pName, defaultStyle);
                createCell(row, 1, cat, defaultStyle);
                createCell(row, 2, sku, defaultStyle);
                createCell(row, 3, color, defaultStyle);
                createCell(row, 4, size, defaultStyle);
                createCell(row, 5, String.format("LKR %,.2f", price), defaultStyle);
                createCell(row, 6, comparePrice.compareTo(BigDecimal.ZERO) > 0 ? String.format("LKR %,.2f", comparePrice) : "—", defaultStyle);
                createCell(row, 7, String.format("LKR %,.2f", costPrice), defaultStyle);
            }

            for (int i = 0; i < headers.length; i++) { sheet.autoSizeColumn(i); sheet.setColumnWidth(i, Math.max(sheet.getColumnWidth(i) + 2500, 4800)); }
            writeWorkbookToResponse(workbook, "Product_Catalog_Report", response);
        }
    }

    // ── 6. Best Selling Products Report ───────────────────────────────────────
    @GetMapping({"/best-selling.excel", "/best-selling.xlsx"})
    public void generateBestSellingReport(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(required = false) String period,
            HttpServletRequest request, HttpServletResponse response) throws IOException {

        if (!isAdminOrStaff(request)) { response.sendError(403, "Access denied."); return; }
        User loggedInUser = getLoggedInUser(request);
        String downloadedByName = loggedInUser != null ? loggedInUser.getFullName() + " (" + loggedInUser.getEmail() + ")" : "Administrator";

        LocalDateTime s = parseStart(start, period);
        LocalDateTime e = parseEnd(end);

        List<Object[]> webTop = em.createQuery(
                "SELECT oi.productVariant.product.name, SUM(oi.quantity), SUM(oi.unitPrice * oi.quantity) " +
                "FROM OrderItem oi WHERE oi.order.createdAt BETWEEN :s AND :e AND oi.order.orderStatus NOT IN ('CANCELLED', 'RETURNED') " +
                "GROUP BY oi.productVariant.product.name", Object[].class).setParameter("s", s).setParameter("e", e).getResultList();

        List<Object[]> posTop = em.createQuery(
                "SELECT poi.productVariant.product.name, SUM(poi.quantity), SUM(poi.unitPrice * poi.quantity) " +
                "FROM PosOrderItem poi WHERE poi.posOrder.createdAt BETWEEN :s AND :e AND poi.posOrder.orderStatus NOT IN ('CANCELLED', 'RETURNED') " +
                "GROUP BY poi.productVariant.product.name", Object[].class).setParameter("s", s).setParameter("e", e).getResultList();

        Map<String, long[]> map = new HashMap<>(); // [qty, revInCents]
        for (Object[] r : webTop) {
            String name = r[0] != null ? r[0].toString() : "Unknown";
            long qty = safeLong(r[1]);
            double rev = safeDouble(r[2]);
            map.putIfAbsent(name, new long[]{0, 0});
            map.get(name)[0] += qty;
            map.get(name)[1] += Math.round(rev * 100);
        }
        for (Object[] r : posTop) {
            String name = r[0] != null ? r[0].toString() : "Unknown";
            long qty = safeLong(r[1]);
            double rev = safeDouble(r[2]);
            map.putIfAbsent(name, new long[]{0, 0});
            map.get(name)[0] += qty;
            map.get(name)[1] += Math.round(rev * 100);
        }

        List<Map.Entry<String, long[]>> sorted = new ArrayList<>(map.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]));

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Best Sellers");
            String[] headers = {"Rank", "Product Name", "Units Sold", "Total Revenue (LKR)"};
            int startRowIndex = createProfessionalBannerHeader(sheet, workbook, "Top Best-Selling Products Ranking Report", downloadedByName, headers.length);

            XSSFFont headerFont = workbook.createFont();
            headerFont.setFontName("Calibri");
            headerFont.setFontHeightInPoints((short) 16);
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            XSSFCellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 30, (byte) 41, (byte) 59}, null));
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            XSSFFont dataFont = workbook.createFont();
            dataFont.setFontName("Calibri");
            dataFont.setFontHeightInPoints((short) 14);

            XSSFCellStyle greenStyle = createDataStyle(workbook, dataFont, new byte[]{(byte) 220, (byte) 252, (byte) 231});
            XSSFCellStyle defaultStyle = createDataStyle(workbook, dataFont, new byte[]{(byte) 255, (byte) 255, (byte) 255});

            Row headerRow = sheet.createRow(startRowIndex);
            headerRow.setHeightInPoints(28);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rank = 1;
            int rowIndex = startRowIndex + 1;
            for (Map.Entry<String, long[]> entry : sorted) {
                Row row = sheet.createRow(rowIndex++);
                row.setHeightInPoints(24);
                XSSFCellStyle rowStyle = rank <= 3 ? greenStyle : defaultStyle;

                createCell(row, 0, "#" + rank, rowStyle);
                createCell(row, 1, entry.getKey(), rowStyle);
                createCell(row, 2, String.valueOf(entry.getValue()[0]), rowStyle);
                createCell(row, 3, String.format("LKR %,.2f", entry.getValue()[1] / 100.0), rowStyle);
                rank++;
            }

            for (int i = 0; i < headers.length; i++) { sheet.autoSizeColumn(i); sheet.setColumnWidth(i, Math.max(sheet.getColumnWidth(i) + 2500, 4800)); }
            writeWorkbookToResponse(workbook, "Best_Selling_Products_Report", response);
        }
    }

    // ── 7. Lowest Selling Products with Variance ──────────────────────────────
    @GetMapping({"/lowest-selling.excel", "/lowest-selling.xlsx"})
    public void generateLowestSellingReport(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(required = false) String period,
            HttpServletRequest request, HttpServletResponse response) throws IOException {

        if (!isAdminOrStaff(request)) { response.sendError(403, "Access denied."); return; }
        User loggedInUser = getLoggedInUser(request);
        String downloadedByName = loggedInUser != null ? loggedInUser.getFullName() + " (" + loggedInUser.getEmail() + ")" : "Administrator";

        LocalDateTime s = parseStart(start, period);
        LocalDateTime e = parseEnd(end);

        List<Object[]> webTop = em.createQuery(
                "SELECT oi.productVariant.product.name, SUM(oi.quantity) " +
                "FROM OrderItem oi WHERE oi.order.createdAt BETWEEN :s AND :e AND oi.order.orderStatus NOT IN ('CANCELLED', 'RETURNED') " +
                "GROUP BY oi.productVariant.product.name", Object[].class).setParameter("s", s).setParameter("e", e).getResultList();

        List<Object[]> posTop = em.createQuery(
                "SELECT poi.productVariant.product.name, SUM(poi.quantity) " +
                "FROM PosOrderItem poi WHERE poi.posOrder.createdAt BETWEEN :s AND :e AND poi.posOrder.orderStatus NOT IN ('CANCELLED', 'RETURNED') " +
                "GROUP BY poi.productVariant.product.name", Object[].class).setParameter("s", s).setParameter("e", e).getResultList();

        Map<String, Long> salesMap = new HashMap<>();
        for (Object[] r : webTop) salesMap.put(r[0].toString(), salesMap.getOrDefault(r[0].toString(), 0L) + safeLong(r[1]));
        for (Object[] r : posTop) salesMap.put(r[0].toString(), salesMap.getOrDefault(r[0].toString(), 0L) + safeLong(r[1]));

        List<Product> allProds = em.createQuery("SELECT p FROM Product p WHERE p.isDeleted = false", Product.class).getResultList();
        double sumQty = 0;
        for (Product p : allProds) sumQty += salesMap.getOrDefault(p.getName(), 0L);
        double avgQty = allProds.isEmpty() ? 0 : sumQty / allProds.size();

        List<Object[]> reportRows = new ArrayList<>();
        for (Product p : allProds) {
            long sold = salesMap.getOrDefault(p.getName(), 0L);
            double variance = sold - avgQty;
            reportRows.add(new Object[]{p.getName(), sold, variance});
        }
        reportRows.sort((a, b) -> Long.compare((Long) a[1], (Long) b[1]));

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Lowest Selling & Variance");
            String[] headers = {"Product Name", "Units Sold in Period", "Average Industry Volume", "Variance vs Average"};
            int startRowIndex = createProfessionalBannerHeader(sheet, workbook, "Slow-Moving Products & Sales Variance Analysis", downloadedByName, headers.length);

            XSSFFont headerFont = workbook.createFont();
            headerFont.setFontName("Calibri");
            headerFont.setFontHeightInPoints((short) 16);
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            XSSFCellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 30, (byte) 41, (byte) 59}, null));
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            XSSFFont dataFont = workbook.createFont();
            dataFont.setFontName("Calibri");
            dataFont.setFontHeightInPoints((short) 14);

            XSSFCellStyle redStyle = createDataStyle(workbook, dataFont, new byte[]{(byte) 254, (byte) 226, (byte) 226});
            XSSFCellStyle yellowStyle = createDataStyle(workbook, dataFont, new byte[]{(byte) 254, (byte) 249, (byte) 195});

            Row headerRow = sheet.createRow(startRowIndex);
            headerRow.setHeightInPoints(28);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = startRowIndex + 1;
            for (Object[] r : reportRows) {
                Row row = sheet.createRow(rowIndex++);
                row.setHeightInPoints(24);
                long sold = (Long) r[1];
                double var = (Double) r[2];

                XSSFCellStyle rowStyle = sold == 0 ? redStyle : yellowStyle;
                createCell(row, 0, r[0].toString(), rowStyle);
                createCell(row, 1, String.valueOf(sold), rowStyle);
                createCell(row, 2, String.format("%.2f", avgQty), rowStyle);
                createCell(row, 3, String.format("%+.2f", var), rowStyle);
            }

            for (int i = 0; i < headers.length; i++) { sheet.autoSizeColumn(i); sheet.setColumnWidth(i, Math.max(sheet.getColumnWidth(i) + 2500, 4800)); }
            writeWorkbookToResponse(workbook, "Lowest_Selling_Products_Variance_Report", response);
        }
    }

    // ── 8. Sales Rep Comparison Report ────────────────────────────────────────
    @GetMapping({"/sales-rep-comparison.excel", "/sales-rep-comparison.xlsx"})
    public void generateSalesRepComparisonReport(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(required = false) String period,
            HttpServletRequest request, HttpServletResponse response) throws IOException {

        if (!isAdminOrStaff(request)) { response.sendError(403, "Access denied."); return; }
        User loggedInUser = getLoggedInUser(request);
        String downloadedByName = loggedInUser != null ? loggedInUser.getFullName() + " (" + loggedInUser.getEmail() + ")" : "Administrator";

        LocalDateTime s = parseStart(start, period);
        LocalDateTime e = parseEnd(end);

        List<Object[]> repList = em.createQuery(
                "SELECT COALESCE(u.fullName, 'Sales Staff'), u.email, COUNT(po), SUM(po.totalAmount) " +
                "FROM PosOrder po LEFT JOIN po.salesRep u WHERE po.orderStatus NOT IN ('CANCELLED', 'RETURNED') AND po.createdAt BETWEEN :s AND :e " +
                "GROUP BY COALESCE(u.fullName, 'Sales Staff'), u.email ORDER BY SUM(po.totalAmount) DESC", Object[].class)
                .setParameter("s", s).setParameter("e", e).getResultList();

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Sales Rep Comparison");
            String[] headers = {"Rank", "Sales Rep Name", "Email Address", "Total POS Orders", "Total Revenue (LKR)", "Average Order Bill (LKR)"};
            int startRowIndex = createProfessionalBannerHeader(sheet, workbook, "Sales Representative Performance Comparison Report", downloadedByName, headers.length);

            XSSFFont headerFont = workbook.createFont();
            headerFont.setFontName("Calibri");
            headerFont.setFontHeightInPoints((short) 16);
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            XSSFCellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 30, (byte) 41, (byte) 59}, null));
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            XSSFFont dataFont = workbook.createFont();
            dataFont.setFontName("Calibri");
            dataFont.setFontHeightInPoints((short) 14);

            XSSFCellStyle greenStyle = createDataStyle(workbook, dataFont, new byte[]{(byte) 220, (byte) 252, (byte) 231});
            XSSFCellStyle defaultStyle = createDataStyle(workbook, dataFont, new byte[]{(byte) 255, (byte) 255, (byte) 255});

            Row headerRow = sheet.createRow(startRowIndex);
            headerRow.setHeightInPoints(28);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rank = 1;
            int rowIndex = startRowIndex + 1;
            for (Object[] r : repList) {
                Row row = sheet.createRow(rowIndex++);
                row.setHeightInPoints(24);
                String name = r[0] != null ? r[0].toString() : "Sales Staff";
                String email = r[1] != null ? r[1].toString() : "N/A";
                long count = safeLong(r[2]);
                double rev = safeDouble(r[3]);
                double avg = count > 0 ? rev / count : 0.0;

                XSSFCellStyle rowStyle = rank == 1 ? greenStyle : defaultStyle;

                createCell(row, 0, "#" + rank, rowStyle);
                createCell(row, 1, name, rowStyle);
                createCell(row, 2, email, rowStyle);
                createCell(row, 3, String.valueOf(count), rowStyle);
                createCell(row, 4, String.format("LKR %,.2f", rev), rowStyle);
                createCell(row, 5, String.format("LKR %,.2f", avg), rowStyle);
                rank++;
            }

            for (int i = 0; i < headers.length; i++) { sheet.autoSizeColumn(i); sheet.setColumnWidth(i, Math.max(sheet.getColumnWidth(i) + 2500, 4800)); }
            writeWorkbookToResponse(workbook, "Sales_Rep_Comparison_Report", response);
        }
    }

    // ── 9. Order Master Report ────────────────────────────────────────────────
    @GetMapping({"/order.excel", "/order.xlsx", "/orders.excel"})
    public void generateOrderMasterReport(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(required = false) String period,
            HttpServletRequest request, HttpServletResponse response) throws IOException {

        if (!isAdminOrStaff(request)) { response.sendError(403, "Access denied."); return; }
        User loggedInUser = getLoggedInUser(request);
        String downloadedByName = loggedInUser != null ? loggedInUser.getFullName() + " (" + loggedInUser.getEmail() + ")" : "Administrator";

        LocalDateTime s = parseStart(start, period);
        LocalDateTime e = parseEnd(end);

        List<Object[]> webOrders = em.createQuery(
                "SELECT o.createdAt, 'WEB', o.id, COALESCE(u.fullName, 'Guest'), COALESCE(o.paymentMethod, 'COD'), o.totalAmount, COALESCE(o.orderStatus, 'PENDING') " +
                "FROM Order o LEFT JOIN o.user u WHERE o.createdAt BETWEEN :s AND :e ORDER BY o.createdAt DESC", Object[].class)
                .setParameter("s", s).setParameter("e", e).getResultList();

        List<Object[]> posOrders = em.createQuery(
                "SELECT po.createdAt, 'POS', po.id, COALESCE(po.customerName, 'Walk-in Customer'), po.paymentMethod, po.totalAmount, COALESCE(po.orderStatus, 'PENDING') " +
                "FROM PosOrder po WHERE po.createdAt BETWEEN :s AND :e ORDER BY po.createdAt DESC", Object[].class)
                .setParameter("s", s).setParameter("e", e).getResultList();

        List<Object[]> all = new ArrayList<>();
        all.addAll(webOrders);
        all.addAll(posOrders);
        all.sort((a, b) -> ((LocalDateTime) b[0]).compareTo((LocalDateTime) a[0]));

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Order Master");
            String[] headers = {"Order Date", "Channel", "Order Code", "Customer Name", "Payment Method", "Total Amount (LKR)", "Order Status"};
            int startRowIndex = createProfessionalBannerHeader(sheet, workbook, "Master Orders Summary & Status Report", downloadedByName, headers.length);

            XSSFFont headerFont = workbook.createFont();
            headerFont.setFontName("Calibri");
            headerFont.setFontHeightInPoints((short) 16);
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            XSSFCellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 30, (byte) 41, (byte) 59}, null));
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            XSSFFont dataFont = workbook.createFont();
            dataFont.setFontName("Calibri");
            dataFont.setFontHeightInPoints((short) 14);

            XSSFCellStyle yellowStyle = createDataStyle(workbook, dataFont, new byte[]{(byte) 254, (byte) 249, (byte) 195});
            XSSFCellStyle greenStyle  = createDataStyle(workbook, dataFont, new byte[]{(byte) 220, (byte) 252, (byte) 231});
            XSSFCellStyle redStyle    = createDataStyle(workbook, dataFont, new byte[]{(byte) 254, (byte) 226, (byte) 226});
            XSSFCellStyle defaultStyle = createDataStyle(workbook, dataFont, new byte[]{(byte) 255, (byte) 255, (byte) 255});

            Row headerRow = sheet.createRow(startRowIndex);
            headerRow.setHeightInPoints(28);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            int rowIndex = startRowIndex + 1;

            for (Object[] r : all) {
                Row row = sheet.createRow(rowIndex++);
                row.setHeightInPoints(24);

                String dateStr = r[0] != null ? ((LocalDateTime) r[0]).format(dtf) : "";
                String channel = r[1].toString();
                String code = (channel.equals("POS") ? "POS-" : "CLC-") + String.format("%05d", (Integer) r[2]);
                String cust = r[3].toString();
                String method = r[4] != null ? r[4].toString() : "";
                BigDecimal amt = r[5] != null ? (BigDecimal) r[5] : BigDecimal.ZERO;
                String status = r[6] != null ? r[6].toString().toUpperCase() : "PENDING";

                XSSFCellStyle rowStyle = defaultStyle;
                if (status.contains("PENDING")) rowStyle = yellowStyle;
                else if (status.contains("DELIVERED") || status.contains("COMPLETED") || status.contains("SHIPPED") || status.contains("PAID")) rowStyle = greenStyle;
                else if (status.contains("CANCEL") || status.contains("RETURN")) rowStyle = redStyle;

                createCell(row, 0, dateStr, rowStyle);
                createCell(row, 1, channel, rowStyle);
                createCell(row, 2, code, rowStyle);
                createCell(row, 3, cust, rowStyle);
                createCell(row, 4, method, rowStyle);
                createCell(row, 5, String.format("LKR %,.2f", amt), rowStyle);
                createCell(row, 6, status, rowStyle);
            }

            for (int i = 0; i < headers.length; i++) { sheet.autoSizeColumn(i); sheet.setColumnWidth(i, Math.max(sheet.getColumnWidth(i) + 2500, 4800)); }
            writeWorkbookToResponse(workbook, "Order_Master_Report", response);
        }
    }

    // ── 10. Payment Reports ───────────────────────────────────────────────────
    @GetMapping({"/payment.excel", "/payment.xlsx", "/payments.excel"})
    public void generatePaymentReport(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(required = false) String period,
            HttpServletRequest request, HttpServletResponse response) throws IOException {

        if (!isAdminOrStaff(request)) { response.sendError(403, "Access denied."); return; }
        User loggedInUser = getLoggedInUser(request);
        String downloadedByName = loggedInUser != null ? loggedInUser.getFullName() + " (" + loggedInUser.getEmail() + ")" : "Administrator";

        LocalDateTime s = parseStart(start, period);
        LocalDateTime e = parseEnd(end);

        List<Payment> payments = em.createQuery(
                "SELECT p FROM Payment p JOIN FETCH p.order o LEFT JOIN FETCH o.user u WHERE p.createdAt BETWEEN :s AND :e ORDER BY p.createdAt DESC", Payment.class)
                .setParameter("s", s).setParameter("e", e).getResultList();

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Payment Transactions");
            String[] headers = {"Payment Date", "Order Code", "Customer Name", "Payment Method", "Transaction / Slip ID", "Amount Paid (LKR)", "Verification Status"};
            int startRowIndex = createProfessionalBannerHeader(sheet, workbook, "Payment Gateway & Slip Transactions Report", downloadedByName, headers.length);

            XSSFFont headerFont = workbook.createFont();
            headerFont.setFontName("Calibri");
            headerFont.setFontHeightInPoints((short) 16);
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            XSSFCellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 30, (byte) 41, (byte) 59}, null));
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            XSSFFont dataFont = workbook.createFont();
            dataFont.setFontName("Calibri");
            dataFont.setFontHeightInPoints((short) 14);

            XSSFCellStyle greenStyle  = createDataStyle(workbook, dataFont, new byte[]{(byte) 220, (byte) 252, (byte) 231});
            XSSFCellStyle yellowStyle = createDataStyle(workbook, dataFont, new byte[]{(byte) 254, (byte) 249, (byte) 195});

            Row headerRow = sheet.createRow(startRowIndex);
            headerRow.setHeightInPoints(28);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            int rowIndex = startRowIndex + 1;

            for (Payment p : payments) {
                Row row = sheet.createRow(rowIndex++);
                row.setHeightInPoints(24);

                String dateStr = p.getCreatedAt() != null ? p.getCreatedAt().format(dtf) : "";
                String ordCode = p.getOrder() != null ? "CLC-" + String.format("%05d", p.getOrder().getId()) : "";
                String cust = (p.getOrder() != null && p.getOrder().getUser() != null) ? p.getOrder().getUser().getFullName() : "Guest";
                String method = p.getPaymentMethod() != null ? p.getPaymentMethod() : "";
                String slip = p.getSlipImageUrl() != null && !p.getSlipImageUrl().isBlank() ? p.getSlipImageUrl() : "—";
                BigDecimal amt = p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO;
                String status = p.getPaymentStatus() != null ? p.getPaymentStatus() : "PENDING";

                XSSFCellStyle rowStyle = "PAID".equalsIgnoreCase(status) ? greenStyle : yellowStyle;

                createCell(row, 0, dateStr, rowStyle);
                createCell(row, 1, ordCode, rowStyle);
                createCell(row, 2, cust, rowStyle);
                createCell(row, 3, method, rowStyle);
                createCell(row, 4, slip, rowStyle);
                createCell(row, 5, String.format("LKR %,.2f", amt), rowStyle);
                createCell(row, 6, status, rowStyle);
            }

            for (int i = 0; i < headers.length; i++) { sheet.autoSizeColumn(i); sheet.setColumnWidth(i, Math.max(sheet.getColumnWidth(i) + 2500, 4800)); }
            writeWorkbookToResponse(workbook, "Payment_Transactions_Report", response);
        }
    }

    // ── 11. Cancelled Orders Report ───────────────────────────────────────────
    @GetMapping({"/cancelled-order.excel", "/cancelled-order.xlsx", "/cancelled-orders.excel"})
    public void generateCancelledOrdersReport(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(required = false) String period,
            HttpServletRequest request, HttpServletResponse response) throws IOException {

        if (!isAdminOrStaff(request)) { response.sendError(403, "Access denied."); return; }
        User loggedInUser = getLoggedInUser(request);
        String downloadedByName = loggedInUser != null ? loggedInUser.getFullName() + " (" + loggedInUser.getEmail() + ")" : "Administrator";

        LocalDateTime s = parseStart(start, period);
        LocalDateTime e = parseEnd(end);

        List<Object[]> webC = em.createQuery(
                "SELECT o.createdAt, 'WEB', o.id, COALESCE(u.fullName, 'Guest'), o.totalAmount, o.cancellationReason " +
                "FROM Order o LEFT JOIN o.user u WHERE o.orderStatus='CANCELLED' AND o.createdAt BETWEEN :s AND :e ORDER BY o.createdAt DESC", Object[].class)
                .setParameter("s", s).setParameter("e", e).getResultList();

        List<Object[]> posC = em.createQuery(
                "SELECT po.createdAt, 'POS', po.id, COALESCE(po.customerName, 'Walk-in Customer'), po.totalAmount, po.cancelReason " +
                "FROM PosOrder po WHERE po.orderStatus='CANCELLED' AND po.createdAt BETWEEN :s AND :e ORDER BY po.createdAt DESC", Object[].class)
                .setParameter("s", s).setParameter("e", e).getResultList();

        List<Object[]> all = new ArrayList<>();
        all.addAll(webC);
        all.addAll(posC);
        all.sort((a, b) -> ((LocalDateTime) b[0]).compareTo((LocalDateTime) a[0]));

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Cancelled Orders");
            String[] headers = {"Date Cancelled", "Channel", "Order Code", "Customer Name", "Amount Lost (LKR)", "Reason for Cancellation"};
            int startRowIndex = createProfessionalBannerHeader(sheet, workbook, "Cancelled Orders & Abandoned Checkouts Report", downloadedByName, headers.length);

            XSSFFont headerFont = workbook.createFont();
            headerFont.setFontName("Calibri");
            headerFont.setFontHeightInPoints((short) 16);
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            XSSFCellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 30, (byte) 41, (byte) 59}, null));
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            XSSFFont dataFont = workbook.createFont();
            dataFont.setFontName("Calibri");
            dataFont.setFontHeightInPoints((short) 14);

            XSSFCellStyle redStyle = createDataStyle(workbook, dataFont, new byte[]{(byte) 254, (byte) 226, (byte) 226});

            Row headerRow = sheet.createRow(startRowIndex);
            headerRow.setHeightInPoints(28);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            int rowIndex = startRowIndex + 1;

            for (Object[] r : all) {
                Row row = sheet.createRow(rowIndex++);
                row.setHeightInPoints(24);

                String dateStr = r[0] != null ? ((LocalDateTime) r[0]).format(dtf) : "";
                String channel = r[1].toString();
                String code = (channel.equals("POS") ? "POS-" : "CLC-") + String.format("%05d", (Integer) r[2]);
                String cust = r[3].toString();
                BigDecimal amt = r[4] != null ? (BigDecimal) r[4] : BigDecimal.ZERO;
                String reason = r[5] != null ? r[5].toString() : "Customer cancelled / Payment timeout";

                createCell(row, 0, dateStr, redStyle);
                createCell(row, 1, channel, redStyle);
                createCell(row, 2, code, redStyle);
                createCell(row, 3, cust, redStyle);
                createCell(row, 4, String.format("LKR %,.2f", amt), redStyle);
                createCell(row, 5, reason, redStyle);
            }

            for (int i = 0; i < headers.length; i++) { sheet.autoSizeColumn(i); sheet.setColumnWidth(i, Math.max(sheet.getColumnWidth(i) + 2500, 4800)); }
            writeWorkbookToResponse(workbook, "Cancelled_Orders_Report", response);
        }
    }

    // ── 12. Returned Orders Report ─────────────────────────────────────────────
    @GetMapping({"/return-order.excel", "/return-order.xlsx", "/returned-orders.excel"})
    public void generateReturnedOrdersReport(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(required = false) String period,
            HttpServletRequest request, HttpServletResponse response) throws IOException {

        if (!isAdminOrStaff(request)) { response.sendError(403, "Access denied."); return; }
        User loggedInUser = getLoggedInUser(request);
        String downloadedByName = loggedInUser != null ? loggedInUser.getFullName() + " (" + loggedInUser.getEmail() + ")" : "Administrator";

        LocalDateTime s = parseStart(start, period);
        LocalDateTime e = parseEnd(end);

        List<Object[]> webR = em.createQuery(
                "SELECT o.createdAt, 'WEB', o.id, COALESCE(u.fullName, 'Guest'), o.totalAmount, o.returnLoss, o.returnReason " +
                "FROM Order o LEFT JOIN o.user u WHERE o.orderStatus='RETURNED' AND o.createdAt BETWEEN :s AND :e ORDER BY o.createdAt DESC", Object[].class)
                .setParameter("s", s).setParameter("e", e).getResultList();

        List<Object[]> posR = em.createQuery(
                "SELECT po.createdAt, 'POS', po.id, COALESCE(po.customerName, 'Walk-in Customer'), po.totalAmount, po.returnLoss, po.returnReason " +
                "FROM PosOrder po WHERE po.orderStatus='RETURNED' AND po.createdAt BETWEEN :s AND :e ORDER BY po.createdAt DESC", Object[].class)
                .setParameter("s", s).setParameter("e", e).getResultList();

        List<Object[]> all = new ArrayList<>();
        all.addAll(webR);
        all.addAll(posR);
        all.sort((a, b) -> ((LocalDateTime) b[0]).compareTo((LocalDateTime) a[0]));

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Returned Orders");
            String[] headers = {"Date Returned", "Channel", "Order Code", "Customer Name", "Total Bill (LKR)", "Net Loss (LKR)", "Return Reason"};
            int startRowIndex = createProfessionalBannerHeader(sheet, workbook, "Returned Orders & Loss Audit Report", downloadedByName, headers.length);

            XSSFFont headerFont = workbook.createFont();
            headerFont.setFontName("Calibri");
            headerFont.setFontHeightInPoints((short) 16);
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            XSSFCellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 30, (byte) 41, (byte) 59}, null));
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            XSSFFont dataFont = workbook.createFont();
            dataFont.setFontName("Calibri");
            dataFont.setFontHeightInPoints((short) 14);

            XSSFCellStyle purpleStyle = createDataStyle(workbook, dataFont, new byte[]{(byte) 243, (byte) 232, (byte) 255});

            Row headerRow = sheet.createRow(startRowIndex);
            headerRow.setHeightInPoints(28);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            int rowIndex = startRowIndex + 1;

            for (Object[] r : all) {
                Row row = sheet.createRow(rowIndex++);
                row.setHeightInPoints(24);

                String dateStr = r[0] != null ? ((LocalDateTime) r[0]).format(dtf) : "";
                String channel = r[1].toString();
                String code = (channel.equals("POS") ? "POS-" : "CLC-") + String.format("%05d", (Integer) r[2]);
                String cust = r[3].toString();
                BigDecimal amt = r[4] != null ? (BigDecimal) r[4] : BigDecimal.ZERO;
                BigDecimal loss = r[5] != null ? (BigDecimal) r[5] : BigDecimal.ZERO;
                String reason = r[6] != null ? r[6].toString() : "Customer returned";

                createCell(row, 0, dateStr, purpleStyle);
                createCell(row, 1, channel, purpleStyle);
                createCell(row, 2, code, purpleStyle);
                createCell(row, 3, cust, purpleStyle);
                createCell(row, 4, String.format("LKR %,.2f", amt), purpleStyle);
                createCell(row, 5, String.format("LKR %,.2f", loss), purpleStyle);
                createCell(row, 6, reason, purpleStyle);
            }

            for (int i = 0; i < headers.length; i++) { sheet.autoSizeColumn(i); sheet.setColumnWidth(i, Math.max(sheet.getColumnWidth(i) + 2500, 4800)); }
            writeWorkbookToResponse(workbook, "Returned_Orders_Report", response);
        }
    }

    // ── 13. POS Sales Report ─────────────────────────────────────────────────
    @GetMapping({"/pos-sale.excel", "/pos-sale.xlsx", "/pos-sales.excel"})
    public void generatePosSalesReport(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(required = false) String period,
            HttpServletRequest request, HttpServletResponse response) throws IOException {

        if (!isAdminOrStaff(request)) { response.sendError(403, "Access denied."); return; }
        User loggedInUser = getLoggedInUser(request);
        String downloadedByName = loggedInUser != null ? loggedInUser.getFullName() + " (" + loggedInUser.getEmail() + ")" : "Administrator";

        LocalDateTime s = parseStart(start, period);
        LocalDateTime e = parseEnd(end);

        List<PosOrder> posList = em.createQuery(
                "SELECT po FROM PosOrder po LEFT JOIN FETCH po.salesRep u WHERE po.createdAt BETWEEN :s AND :e ORDER BY po.createdAt DESC", PosOrder.class)
                .setParameter("s", s).setParameter("e", e).getResultList();

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("POS Sales");
            String[] headers = {"POS Order Code", "Date & Time", "Sales Rep", "Customer Name", "Phone", "Subtotal (LKR)", "Discount (LKR)", "Delivery Fee (LKR)", "Total Bill (LKR)", "Advance Paid", "COD Balance", "Order Status"};
            int startRowIndex = createProfessionalBannerHeader(sheet, workbook, "POS & WhatsApp Orders Detailed Report", downloadedByName, headers.length);

            XSSFFont headerFont = workbook.createFont();
            headerFont.setFontName("Calibri");
            headerFont.setFontHeightInPoints((short) 16);
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            XSSFCellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 30, (byte) 41, (byte) 59}, null));
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            XSSFFont dataFont = workbook.createFont();
            dataFont.setFontName("Calibri");
            dataFont.setFontHeightInPoints((short) 14);

            XSSFCellStyle greenStyle  = createDataStyle(workbook, dataFont, new byte[]{(byte) 220, (byte) 252, (byte) 231});
            XSSFCellStyle yellowStyle = createDataStyle(workbook, dataFont, new byte[]{(byte) 254, (byte) 249, (byte) 195});

            Row headerRow = sheet.createRow(startRowIndex);
            headerRow.setHeightInPoints(28);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            int rowIndex = startRowIndex + 1;

            for (PosOrder po : posList) {
                Row row = sheet.createRow(rowIndex++);
                row.setHeightInPoints(24);

                String code = "POS-" + String.format("%05d", po.getId());
                String dateStr = po.getCreatedAt() != null ? po.getCreatedAt().format(dtf) : "";
                String rep = po.getSalesRep() != null ? po.getSalesRep().getFullName() : "Sales Staff";
                String cust = po.getCustomerName() != null ? po.getCustomerName() : "Walk-in Customer";
                String phone = po.getPhone1() != null ? po.getPhone1() : "";
                BigDecimal sub = po.getSubtotal() != null ? po.getSubtotal() : BigDecimal.ZERO;
                BigDecimal disc = po.getDiscountAmount() != null ? po.getDiscountAmount() : BigDecimal.ZERO;
                BigDecimal del = po.getDeliveryCharge() != null ? po.getDeliveryCharge() : BigDecimal.ZERO;
                BigDecimal amt = po.getTotalAmount() != null ? po.getTotalAmount() : BigDecimal.ZERO;
                BigDecimal adv = po.getAdvancePaid() != null ? po.getAdvancePaid() : BigDecimal.ZERO;
                BigDecimal cod = po.getCodBalance() != null ? po.getCodBalance() : BigDecimal.ZERO;
                String status = po.getOrderStatus() != null ? po.getOrderStatus() : "PENDING";

                XSSFCellStyle rowStyle = "SHIPPED".equals(status) || "DELIVERED".equals(status) || "COMPLETED".equals(status) ? greenStyle : yellowStyle;

                createCell(row, 0, code, rowStyle);
                createCell(row, 1, dateStr, rowStyle);
                createCell(row, 2, rep, rowStyle);
                createCell(row, 3, cust, rowStyle);
                createCell(row, 4, phone, rowStyle);
                createCell(row, 5, String.format("LKR %,.2f", sub), rowStyle);
                createCell(row, 6, String.format("LKR %,.2f", disc), rowStyle);
                createCell(row, 7, String.format("LKR %,.2f", del), rowStyle);
                createCell(row, 8, String.format("LKR %,.2f", amt), rowStyle);
                createCell(row, 9, String.format("LKR %,.2f", adv), rowStyle);
                createCell(row, 10, String.format("LKR %,.2f", cod), rowStyle);
                createCell(row, 11, status, rowStyle);
            }

            for (int i = 0; i < headers.length; i++) { sheet.autoSizeColumn(i); sheet.setColumnWidth(i, Math.max(sheet.getColumnWidth(i) + 2500, 4800)); }
            writeWorkbookToResponse(workbook, "POS_Sales_Report", response);
        }
    }

    // ── 14. Web Sales Report ─────────────────────────────────────────────────
    @GetMapping({"/web-sale.excel", "/web-sale.xlsx", "/web-sales.excel"})
    public void generateWebSalesReport(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(required = false) String period,
            HttpServletRequest request, HttpServletResponse response) throws IOException {

        if (!isAdminOrStaff(request)) { response.sendError(403, "Access denied."); return; }
        User loggedInUser = getLoggedInUser(request);
        String downloadedByName = loggedInUser != null ? loggedInUser.getFullName() + " (" + loggedInUser.getEmail() + ")" : "Administrator";

        LocalDateTime s = parseStart(start, period);
        LocalDateTime e = parseEnd(end);

        List<Order> webList = em.createQuery(
                "SELECT o FROM Order o LEFT JOIN FETCH o.user u WHERE o.createdAt BETWEEN :s AND :e ORDER BY o.createdAt DESC", Order.class)
                .setParameter("s", s).setParameter("e", e).getResultList();

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Web Sales");
            String[] headers = {"Order Code", "Date & Time", "Customer Name", "Email Address", "Shipping Address", "Total Bill (LKR)", "Payment Status", "Order Status"};
            int startRowIndex = createProfessionalBannerHeader(sheet, workbook, "Website Online Checkout Orders Report", downloadedByName, headers.length);

            XSSFFont headerFont = workbook.createFont();
            headerFont.setFontName("Calibri");
            headerFont.setFontHeightInPoints((short) 16);
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            XSSFCellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 30, (byte) 41, (byte) 59}, null));
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            XSSFFont dataFont = workbook.createFont();
            dataFont.setFontName("Calibri");
            dataFont.setFontHeightInPoints((short) 14);

            XSSFCellStyle greenStyle  = createDataStyle(workbook, dataFont, new byte[]{(byte) 220, (byte) 252, (byte) 231});
            XSSFCellStyle yellowStyle = createDataStyle(workbook, dataFont, new byte[]{(byte) 254, (byte) 249, (byte) 195});

            Row headerRow = sheet.createRow(startRowIndex);
            headerRow.setHeightInPoints(28);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            int rowIndex = startRowIndex + 1;

            for (Order o : webList) {
                Row row = sheet.createRow(rowIndex++);
                row.setHeightInPoints(24);

                String code = "CLC-" + String.format("%05d", o.getId());
                String dateStr = o.getCreatedAt() != null ? o.getCreatedAt().format(dtf) : "";
                String cust = o.getUser() != null ? o.getUser().getFullName() : "Guest";
                String email = o.getUser() != null ? o.getUser().getEmail() : "N/A";
                String address = (o.getShippingAddress() != null && o.getShippingAddress().getStreet() != null) 
                        ? o.getShippingAddress().getStreet() + (o.getShippingAddress().getCity() != null ? ", " + o.getShippingAddress().getCity() : "") 
                        : "N/A";
                BigDecimal amt = o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO;
                String pStatus = o.getPaymentStatus() != null ? o.getPaymentStatus() : "PENDING";
                String oStatus = o.getOrderStatus() != null ? o.getOrderStatus() : "PENDING";

                XSSFCellStyle rowStyle = "PAID".equals(pStatus) ? greenStyle : yellowStyle;

                createCell(row, 0, code, rowStyle);
                createCell(row, 1, dateStr, rowStyle);
                createCell(row, 2, cust, rowStyle);
                createCell(row, 3, email, rowStyle);
                createCell(row, 4, address, rowStyle);
                createCell(row, 5, String.format("LKR %,.2f", amt), rowStyle);
                createCell(row, 6, pStatus, rowStyle);
                createCell(row, 7, oStatus, rowStyle);
            }

            for (int i = 0; i < headers.length; i++) { sheet.autoSizeColumn(i); sheet.setColumnWidth(i, Math.max(sheet.getColumnWidth(i) + 2500, 4800)); }
            writeWorkbookToResponse(workbook, "Web_Sales_Report", response);
        }
    }

    // ── 15. Sales Rep Individual Excel Report ──────────────────────────────────
    @GetMapping({"/sales-rep.excel", "/sales-rep.xlsx"})
    public void generateSalesRepReport(
            @RequestParam String email,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(required = false) String period,
            HttpServletRequest request, HttpServletResponse response) throws IOException {

        if (!isAdminOrStaff(request)) { response.sendError(403, "Access denied."); return; }
        User loggedInUser = getLoggedInUser(request);
        String downloadedByName = loggedInUser != null ? loggedInUser.getFullName() + " (" + loggedInUser.getEmail() + ")" : "Administrator";

        LocalDateTime s = parseStart(start, period);
        LocalDateTime e = parseEnd(end);

        List<PosOrder> posOrders = em.createQuery(
                "SELECT po FROM PosOrder po WHERE po.salesRep.email = :email AND po.createdAt BETWEEN :s AND :e ORDER BY po.createdAt DESC",
                PosOrder.class)
                .setParameter("email", email)
                .setParameter("s", s)
                .setParameter("e", e)
                .getResultList();

        String repName = "Unknown Sales Rep";
        try {
            User rep = em.createQuery("SELECT u FROM User u WHERE u.email = :email", User.class)
                    .setParameter("email", email)
                    .getSingleResult();
            repName = rep.getFullName();
        } catch (Exception ex) {
            if (!posOrders.isEmpty() && posOrders.get(0).getSalesRep() != null) {
                repName = posOrders.get(0).getSalesRep().getFullName();
            }
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Performance Ledger");
            String[] headers = {"Order Code", "Date", "Customer Name", "Phone Number", "Subtotal (LKR)", "Discount (LKR)", "Delivery Fee (LKR)", "Total Amount (LKR)", "Advance Paid", "COD Balance", "Order Status", "Is Custom"};
            int startRowIndex = createProfessionalBannerHeader(sheet, workbook, "Sales Rep Performance Report (" + repName + ")", downloadedByName, headers.length);

            XSSFFont headerFont = workbook.createFont();
            headerFont.setFontName("Calibri");
            headerFont.setFontHeightInPoints((short) 16);
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            XSSFCellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 30, (byte) 41, (byte) 59}, null));
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            XSSFFont dataFont = workbook.createFont();
            dataFont.setFontName("Calibri");
            dataFont.setFontHeightInPoints((short) 14);

            XSSFCellStyle yellowStyle = createDataStyle(workbook, dataFont, new byte[]{(byte) 254, (byte) 249, (byte) 195});
            XSSFCellStyle greenStyle  = createDataStyle(workbook, dataFont, new byte[]{(byte) 220, (byte) 252, (byte) 231});
            XSSFCellStyle redStyle    = createDataStyle(workbook, dataFont, new byte[]{(byte) 254, (byte) 226, (byte) 226});
            XSSFCellStyle defaultStyle = createDataStyle(workbook, dataFont, new byte[]{(byte) 255, (byte) 255, (byte) 255});

            Row headerRow = sheet.createRow(startRowIndex);
            headerRow.setHeightInPoints(28);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            BigDecimal totalSales = BigDecimal.ZERO;
            BigDecimal totalAdvance = BigDecimal.ZERO;
            BigDecimal totalCod = BigDecimal.ZERO;
            int rowIndex = startRowIndex + 1;

            for (PosOrder po : posOrders) {
                Row row = sheet.createRow(rowIndex++);
                row.setHeightInPoints(24);

                String ordId   = "POS-" + String.format("%05d", po.getId());
                String dateStr = po.getCreatedAt() != null ? po.getCreatedAt().format(dtf) : "";
                String cust    = po.getCustomerName() != null ? po.getCustomerName() : "Walk-in Customer";
                String phone   = po.getPhone1() != null ? po.getPhone1() : "";
                BigDecimal sub = po.getSubtotal() != null ? po.getSubtotal() : BigDecimal.ZERO;
                BigDecimal disc = po.getDiscountAmount() != null ? po.getDiscountAmount() : BigDecimal.ZERO;
                BigDecimal del = po.getDeliveryCharge() != null ? po.getDeliveryCharge() : BigDecimal.ZERO;
                BigDecimal amt = po.getTotalAmount() != null ? po.getTotalAmount() : BigDecimal.ZERO;
                BigDecimal adv = po.getAdvancePaid() != null ? po.getAdvancePaid() : BigDecimal.ZERO;
                BigDecimal cod = po.getCodBalance() != null ? po.getCodBalance() : BigDecimal.ZERO;
                String status  = po.getOrderStatus() != null ? po.getOrderStatus().toUpperCase() : "PENDING";
                String isCustom = (po.getIsCustom() != null && po.getIsCustom()) ? "YES" : "NO";

                totalSales = totalSales.add(amt);
                totalAdvance = totalAdvance.add(adv);
                totalCod = totalCod.add(cod);

                XSSFCellStyle rowStyle = defaultStyle;
                if (status.contains("PENDING")) rowStyle = yellowStyle;
                else if (status.contains("PAID") || status.contains("COMPLETED") || status.contains("DELIVERED") || status.contains("SHIPPED")) rowStyle = greenStyle;
                else if (status.contains("CANCEL")) rowStyle = redStyle;

                createCell(row, 0, ordId, rowStyle);
                createCell(row, 1, dateStr, rowStyle);
                createCell(row, 2, cust, rowStyle);
                createCell(row, 3, phone, rowStyle);
                createCell(row, 4, String.format("LKR %,.2f", sub), rowStyle);
                createCell(row, 5, String.format("LKR %,.2f", disc), rowStyle);
                createCell(row, 6, String.format("LKR %,.2f", del), rowStyle);
                createCell(row, 7, String.format("LKR %,.2f", amt), rowStyle);
                createCell(row, 8, String.format("LKR %,.2f", adv), rowStyle);
                createCell(row, 9, String.format("LKR %,.2f", cod), rowStyle);
                createCell(row, 10, status, rowStyle);
                createCell(row, 11, isCustom, rowStyle);
            }

            for (int i = 0; i < headers.length; i++) { sheet.autoSizeColumn(i); sheet.setColumnWidth(i, Math.max(sheet.getColumnWidth(i) + 2500, 4800)); }
            writeWorkbookToResponse(workbook, "SalesRep_" + email + "_Report", response);
        }
    }

    // ── 16. Warranty Claims Report ─────────────────────────────────────────────
    @GetMapping({"/warranty-claim.excel", "/warranty-claim.xlsx", "/warranty-claims.excel"})
    public void generateWarrantyClaimReport(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(required = false) String period,
            HttpServletRequest request, HttpServletResponse response) throws IOException {

        if (!isAdminOrStaff(request)) { response.sendError(403, "Access denied."); return; }
        User loggedInUser = getLoggedInUser(request);
        String downloadedByName = loggedInUser != null ? loggedInUser.getFullName() + " (" + loggedInUser.getEmail() + ")" : "Administrator";

        LocalDateTime s = parseStart(start, period);
        LocalDateTime e = parseEnd(end);

        List<WarrantyClaim> claims = em.createQuery(
                "SELECT wc FROM WarrantyClaim wc WHERE wc.createdAt BETWEEN :s AND :e ORDER BY wc.createdAt DESC", WarrantyClaim.class)
                .setParameter("s", s).setParameter("e", e).getResultList();

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Warranty Claims");
            String[] headers = {"Date & Time", "Order Code", "Customer Name", "Phone Number", "Product Name", "Claim Reason", "Claim Status", "Admin Notes"};
            int startRowIndex = createProfessionalBannerHeader(sheet, workbook, "Warranty Claim Audit & Fulfillment Report", downloadedByName, headers.length);

            XSSFFont headerFont = workbook.createFont();
            headerFont.setFontName("Calibri");
            headerFont.setFontHeightInPoints((short) 16);
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            XSSFCellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 30, (byte) 41, (byte) 59}, null));
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            XSSFFont dataFont = workbook.createFont();
            dataFont.setFontName("Calibri");
            dataFont.setFontHeightInPoints((short) 14);

            XSSFCellStyle greenStyle  = createDataStyle(workbook, dataFont, new byte[]{(byte) 220, (byte) 252, (byte) 231});
            XSSFCellStyle yellowStyle = createDataStyle(workbook, dataFont, new byte[]{(byte) 254, (byte) 249, (byte) 195});
            XSSFCellStyle blueStyle   = createDataStyle(workbook, dataFont, new byte[]{(byte) 219, (byte) 234, (byte) 254});
            XSSFCellStyle redStyle    = createDataStyle(workbook, dataFont, new byte[]{(byte) 254, (byte) 226, (byte) 226});

            Row headerRow = sheet.createRow(startRowIndex);
            headerRow.setHeightInPoints(28);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            int rowIndex = startRowIndex + 1;

            for (WarrantyClaim wc : claims) {
                Row row = sheet.createRow(rowIndex++);
                row.setHeightInPoints(24);

                String dateStr = wc.getCreatedAt() != null ? wc.getCreatedAt().format(dtf) : "";
                String code = (wc.getOrderType() != null && wc.getOrderType().equals("POS") ? "POS-" : "CLC-") + String.format("%05d", wc.getOrderId());
                String cust = wc.getCustomerName() != null ? wc.getCustomerName() : "Customer";
                String phone = wc.getCustomerPhone() != null ? wc.getCustomerPhone() : "";
                String prod = wc.getProductName() != null ? wc.getProductName() : "";
                String reason = wc.getClaimReason() != null ? wc.getClaimReason() : "";
                String status = wc.getClaimStatus() != null ? wc.getClaimStatus() : "PENDING_REVIEW";
                String notes = wc.getAdminNotes() != null ? wc.getAdminNotes() : "";

                XSSFCellStyle rowStyle = yellowStyle;
                if ("ONGOING".equals(status)) rowStyle = blueStyle;
                else if ("WARRANTY_CLAIMED".equals(status)) rowStyle = greenStyle;
                else if ("REJECTED".equals(status)) rowStyle = redStyle;

                createCell(row, 0, dateStr, rowStyle);
                createCell(row, 1, code, rowStyle);
                createCell(row, 2, cust, rowStyle);
                createCell(row, 3, phone, rowStyle);
                createCell(row, 4, prod, rowStyle);
                createCell(row, 5, reason, rowStyle);
                createCell(row, 6, status, rowStyle);
                createCell(row, 7, notes, rowStyle);
            }

            for (int i = 0; i < headers.length; i++) { sheet.autoSizeColumn(i); sheet.setColumnWidth(i, Math.max(sheet.getColumnWidth(i) + 2500, 4800)); }
            writeWorkbookToResponse(workbook, "Warranty_Claims_Report", response);
        }
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
}
