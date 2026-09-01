package com.ceylonletterco.controller;

import com.ceylonletterco.entity.*;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * PersonalAchievementController – handles staff personal achievement dashboard,
 * sales target updates, and styled multi-sheet Excel sales report exports.
 */
@RestController
@RequestMapping("/api/staff")
@Transactional
public class PersonalAchievementController {

    @PersistenceContext
    private EntityManager em;

    private User getStaffUser(HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        if (s == null) return null;
        User u = (User) s.getAttribute("loggedInUser");
        if (u == null) return null;
        return em.find(User.class, u.getId());
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    // ── GET /api/staff/achievement ───────────────────────────────────────────
    @GetMapping(value = "/achievement", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<String> getPersonalAchievement(HttpServletRequest request) {
        User staff = getStaffUser(request);
        if (staff == null) {
            return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");
        }

        try {
            LocalDate today = LocalDate.now();
            LocalDateTime startToday = today.atStartOfDay();
            LocalDateTime endToday = today.atTime(23, 59, 59);

            LocalDate firstDayOfMonth = today.withDayOfMonth(1);
            LocalDateTime startMonth = firstDayOfMonth.atStartOfDay();

            // POS orders for this sales rep today
            List<PosOrder> posOrdersToday = em.createQuery(
                    "SELECT o FROM PosOrder o WHERE o.salesRep.id = :uid AND o.createdAt >= :start AND o.createdAt <= :end AND o.orderStatus != 'CANCELLED'",
                    PosOrder.class)
                    .setParameter("uid", staff.getId())
                    .setParameter("start", startToday)
                    .setParameter("end", endToday)
                    .getResultList();

            BigDecimal todaySales = BigDecimal.ZERO;
            for (PosOrder o : posOrdersToday) {
                if (o.getTotalAmount() != null) todaySales = todaySales.add(o.getTotalAmount());
            }
            int todayOrdersCount = posOrdersToday.size();

            // POS orders for this sales rep this month
            List<PosOrder> posOrdersMonth = em.createQuery(
                    "SELECT o FROM PosOrder o WHERE o.salesRep.id = :uid AND o.createdAt >= :start AND o.createdAt <= :end AND o.orderStatus != 'CANCELLED'",
                    PosOrder.class)
                    .setParameter("uid", staff.getId())
                    .setParameter("start", startMonth)
                    .setParameter("end", endToday)
                    .getResultList();

            BigDecimal monthSales = BigDecimal.ZERO;
            for (PosOrder o : posOrdersMonth) {
                if (o.getTotalAmount() != null) monthSales = monthSales.add(o.getTotalAmount());
            }
            int monthOrdersCount = posOrdersMonth.size();

            // Web orders if user is admin/staff
            List<Order> webOrdersToday = em.createQuery(
                    "SELECT o FROM Order o WHERE o.createdAt >= :start AND o.createdAt <= :end AND o.orderStatus != 'CANCELLED'",
                    Order.class)
                    .setParameter("start", startToday)
                    .setParameter("end", endToday)
                    .getResultList();

            // Target calculations
            BigDecimal targetDailySales = staff.getDailySalesTarget();
            Integer targetDailyOrders = staff.getDailyOrdersTarget();
            BigDecimal targetMonthlySales = staff.getMonthlySalesTarget();

            double dailySalesPct = targetDailySales.compareTo(BigDecimal.ZERO) > 0
                    ? todaySales.multiply(new BigDecimal("100")).divide(targetDailySales, 1, RoundingMode.HALF_UP).doubleValue()
                    : 0.0;
            double dailyOrdersPct = targetDailyOrders > 0
                    ? ((double) todayOrdersCount / targetDailyOrders) * 100.0
                    : 0.0;
            double monthlySalesPct = targetMonthlySales.compareTo(BigDecimal.ZERO) > 0
                    ? monthSales.multiply(new BigDecimal("100")).divide(targetMonthlySales, 1, RoundingMode.HALF_UP).doubleValue()
                    : 0.0;

            // 14-day daily sales trend chart data
            StringBuilder trendJson = new StringBuilder("[");
            DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("MMM dd");
            DateTimeFormatter sqlFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            for (int i = 13; i >= 0; i--) {
                LocalDate d = today.minusDays(i);
                LocalDateTime dStart = d.atStartOfDay();
                LocalDateTime dEnd = d.atTime(23, 59, 59);

                List<PosOrder> dayOrders = em.createQuery(
                        "SELECT o FROM PosOrder o WHERE o.salesRep.id = :uid AND o.createdAt >= :start AND o.createdAt <= :end AND o.orderStatus != 'CANCELLED'",
                        PosOrder.class)
                        .setParameter("uid", staff.getId())
                        .setParameter("start", dStart)
                        .setParameter("end", dEnd)
                        .getResultList();

                BigDecimal dayTotal = BigDecimal.ZERO;
                for (PosOrder po : dayOrders) {
                    if (po.getTotalAmount() != null) dayTotal = dayTotal.add(po.getTotalAmount());
                }

                if (i < 13) trendJson.append(",");
                trendJson.append("{")
                        .append("\"date\":\"").append(d.format(dayFmt)).append("\",")
                        .append("\"rawDate\":\"").append(d.format(sqlFmt)).append("\",")
                        .append("\"sales\":").append(dayTotal).append(",")
                        .append("\"orders\":").append(dayOrders.size())
                        .append("}");
            }
            trendJson.append("]");

            StringBuilder sb = new StringBuilder("{");
            sb.append("\"success\":true,");
            sb.append("\"profile\":{")
                    .append("\"id\":").append(staff.getId()).append(",")
                    .append("\"fullName\":\"").append(esc(staff.getFullName())).append("\",")
                    .append("\"email\":\"").append(esc(staff.getEmail())).append("\",")
                    .append("\"role\":\"").append(esc(staff.getRole())).append("\",")
                    .append("\"profileImageUrl\":\"").append(esc(staff.getProfileImageUrl())).append("\"")
                    .append("},");
            sb.append("\"targets\":{")
                    .append("\"dailySalesTarget\":").append(targetDailySales).append(",")
                    .append("\"dailyOrdersTarget\":").append(targetDailyOrders).append(",")
                    .append("\"monthlySalesTarget\":").append(targetMonthlySales)
                    .append("},");
            sb.append("\"stats\":{")
                    .append("\"todaySales\":").append(todaySales).append(",")
                    .append("\"todayOrders\":").append(todayOrdersCount).append(",")
                    .append("\"monthlySales\":").append(monthSales).append(",")
                    .append("\"monthlyOrders\":").append(monthOrdersCount).append(",")
                    .append("\"dailySalesPct\":").append(Math.min(dailySalesPct, 100.0)).append(",")
                    .append("\"dailyOrdersPct\":").append(Math.min(dailyOrdersPct, 100.0)).append(",")
                    .append("\"monthlySalesPct\":").append(Math.min(monthlySalesPct, 100.0))
                    .append("},");
            sb.append("\"trend\":").append(trendJson);
            sb.append("}");

            return ResponseEntity.ok(sb.toString());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── POST /api/staff/targets ──────────────────────────────────────────────
    @PostMapping(value = "/targets", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> updateTargets(@RequestBody JsonNode body, HttpServletRequest request) {
        User staff = getStaffUser(request);
        if (staff == null) {
            return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");
        }

        try {
            if (!body.path("dailySalesTarget").isMissingNode()) {
                staff.setDailySalesTarget(new BigDecimal(body.path("dailySalesTarget").asText("50000.00")));
            }
            if (!body.path("dailyOrdersTarget").isMissingNode()) {
                staff.setDailyOrdersTarget(body.path("dailyOrdersTarget").asInt(10));
            }
            if (!body.path("monthlySalesTarget").isMissingNode()) {
                staff.setMonthlySalesTarget(new BigDecimal(body.path("monthlySalesTarget").asText("1000000.00")));
            }
            em.merge(staff);
            em.flush();

            return ResponseEntity.ok("{\"success\":true,\"message\":\"Targets updated successfully!\"}");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── GET /api/staff/excel-report ──────────────────────────────────────────
    @GetMapping(value = "/excel-report")
    public void downloadExcelReport(HttpServletRequest request, HttpServletResponse response) {
        User staff = getStaffUser(request);
        if (staff == null) {
            response.setStatus(403);
            return;
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            // Fetch all POS orders for this Sales Rep
            List<PosOrder> allOrders = em.createQuery(
                    "SELECT o FROM PosOrder o WHERE o.salesRep.id = :uid ORDER BY o.createdAt DESC", PosOrder.class)
                    .setParameter("uid", staff.getId())
                    .getResultList();

            // Group orders by Year-Month (e.g., "August 2026", "July 2026")
            Map<String, List<PosOrder>> monthlyOrdersMap = new LinkedHashMap<>();
            DateTimeFormatter monthKeyFmt = DateTimeFormatter.ofPattern("MMMM yyyy");
            DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

            if (allOrders.isEmpty()) {
                monthlyOrdersMap.put("Current Month", new ArrayList<>());
            } else {
                for (PosOrder o : allOrders) {
                    String monthKey = o.getCreatedAt() != null ? o.getCreatedAt().format(monthKeyFmt) : "All Time";
                    monthlyOrdersMap.computeIfAbsent(monthKey, k -> new ArrayList<>()).add(o);
                }
            }

            // Create Styles
            // Header Font (16pt, Bold, White)
            XSSFFont headerFont = workbook.createFont();
            headerFont.setFontName("Calibri");
            headerFont.setFontHeightInPoints((short) 16);
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            XSSFCellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 30, (byte) 41, (byte) 59}, null)); // #1E293B
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setBorderTop(BorderStyle.MEDIUM);
            headerStyle.setBorderBottom(BorderStyle.MEDIUM);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            short hBorderColor = IndexedColors.GREY_50_PERCENT.getIndex();
            headerStyle.setTopBorderColor(hBorderColor);
            headerStyle.setBottomBorderColor(hBorderColor);
            headerStyle.setLeftBorderColor(hBorderColor);
            headerStyle.setRightBorderColor(hBorderColor);

            // Data Font (14pt)
            XSSFFont dataFont = workbook.createFont();
            dataFont.setFontName("Calibri");
            dataFont.setFontHeightInPoints((short) 14);

            // Row Color Styles:
            // 🟡 Pending: Light Yellow (#FEF9C3)
            XSSFCellStyle pendingStyle = createDataStyle(workbook, dataFont, new byte[]{(byte) 254, (byte) 249, (byte) 195});
            // 🟢 Shipped / Delivered: Light Green (#DCFCE7)
            XSSFCellStyle greenStyle = createDataStyle(workbook, dataFont, new byte[]{(byte) 220, (byte) 252, (byte) 231});
            // 🔴 Cancelled: Light Red (#FEE2E2)
            XSSFCellStyle redStyle = createDataStyle(workbook, dataFont, new byte[]{(byte) 254, (byte) 226, (byte) 226});
            // 🟣 Return / Returned: Light Purple (#F3E8FF)
            XSSFCellStyle purpleStyle = createDataStyle(workbook, dataFont, new byte[]{(byte) 243, (byte) 232, (byte) 255});
            // Standard White Data Style
            XSSFCellStyle defaultDataStyle = createDataStyle(workbook, dataFont, new byte[]{(byte) 255, (byte) 255, (byte) 255});

            // Month Total Summary Font (18pt, Bold)
            XSSFFont totalFont = workbook.createFont();
            totalFont.setFontName("Calibri");
            totalFont.setFontHeightInPoints((short) 18);
            totalFont.setBold(true);
            totalFont.setColor(new XSSFColor(new byte[]{(byte) 15, (byte) 23, (byte) 42}, null));

            XSSFCellStyle totalStyle = workbook.createCellStyle();
            totalStyle.setFont(totalFont);
            totalStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 226, (byte) 232, (byte) 240}, null)); // #E2E8F0
            totalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            totalStyle.setAlignment(HorizontalAlignment.LEFT);
            totalStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            totalStyle.setBorderTop(BorderStyle.DOUBLE);
            totalStyle.setBorderBottom(BorderStyle.DOUBLE);
            totalStyle.setBorderLeft(BorderStyle.THIN);
            totalStyle.setBorderRight(BorderStyle.THIN);
            short tBorderColor = IndexedColors.GREY_50_PERCENT.getIndex();
            totalStyle.setTopBorderColor(tBorderColor);
            totalStyle.setBottomBorderColor(tBorderColor);
            totalStyle.setLeftBorderColor(tBorderColor);
            totalStyle.setRightBorderColor(tBorderColor);

            String[] headers = {
                    "Date", "Order Code", "Customer Name", "Order Items (Qty)",
                    "Total Bill (LKR)", "Discount (LKR)", "COD", "Adv + COD", "Full Paid",
                    "Status", "Tracking Number"
            };

            String downloadedByName = staff.getFullName() + " (" + staff.getEmail() + ")";
            String generationTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            for (Map.Entry<String, List<PosOrder>> entry : monthlyOrdersMap.entrySet()) {
                String sheetName = entry.getKey();
                XSSFSheet sheet = workbook.createSheet(sheetName);
                sheet.setDisplayGridlines(true);

                // Row 0: Main Company Branding Banner (Brand Dark #1A1816 with Gold Text #C9A96E)
                Row titleRow = sheet.createRow(0);
                titleRow.setHeightInPoints(44);

                XSSFFont titleFont = workbook.createFont();
                titleFont.setFontName("Calibri");
                titleFont.setFontHeightInPoints((short) 20);
                titleFont.setBold(true);
                titleFont.setColor(new XSSFColor(new byte[]{(byte) 201, (byte) 169, (byte) 110}, null)); // Gold Accent #C9A96E

                XSSFCellStyle titleStyle = workbook.createCellStyle();
                titleStyle.setFont(titleFont);
                titleStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 26, (byte) 24, (byte) 22}, null)); // Brand Dark #1A1816
                titleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                titleStyle.setAlignment(HorizontalAlignment.CENTER);
                titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);

                for (int c = 0; c < headers.length; c++) {
                    Cell cell = titleRow.createCell(c);
                    if (c == 0) cell.setCellValue("CEYLON LETTER CO. — SALES PERFORMANCE REPORT");
                    cell.setCellStyle(titleStyle);
                }
                sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, headers.length - 1));

                // Subtitle Style (Brand Surface Background #2A2622, Cream Text #FEF9F3)
                XSSFFont subFont = workbook.createFont();
                subFont.setFontName("Calibri");
                subFont.setFontHeightInPoints((short) 12);
                subFont.setBold(true);
                subFont.setColor(new XSSFColor(new byte[]{(byte) 254, (byte) 249, (byte) 243}, null)); // #FEF9F3

                XSSFCellStyle subStyle = workbook.createCellStyle();
                subStyle.setFont(subFont);
                subStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 42, (byte) 38, (byte) 34}, null)); // Brand Surface #2A2622
                subStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                subStyle.setVerticalAlignment(VerticalAlignment.CENTER);

                // Row 1: Sales Rep & Month
                Row row1 = sheet.createRow(1);
                row1.setHeightInPoints(22);
                for (int c = 0; c < headers.length; c++) {
                    Cell cell = row1.createCell(c);
                    if (c == 0) cell.setCellValue("  Sales Rep: " + staff.getFullName() + "   |   Period: " + sheetName);
                    cell.setCellStyle(subStyle);
                }
                sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(1, 1, 0, headers.length - 1));

                // Row 2: Metadata (Generation Time + Downloaded By)
                Row row2 = sheet.createRow(2);
                row2.setHeightInPoints(22);
                for (int c = 0; c < headers.length; c++) {
                    Cell cell = row2.createCell(c);
                    if (c == 0) cell.setCellValue("  Report Generated On: " + generationTime + "   |   Downloaded By: " + downloadedByName);
                    cell.setCellStyle(subStyle);
                }
                sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(2, 2, 0, headers.length - 1));

                // Row 3: Blank spacing row
                Row blankRow = sheet.createRow(3);
                blankRow.setHeightInPoints(12);

                // Table Header Row (Row 4)
                Row headerRow = sheet.createRow(4);
                headerRow.setHeightInPoints(32);
                for (int col = 0; col < headers.length; col++) {
                    Cell cell = headerRow.createCell(col);
                    cell.setCellValue(headers[col]);
                    cell.setCellStyle(headerStyle);
                }

                int rowIndex = 5;
                BigDecimal monthTotalSale = BigDecimal.ZERO;

                for (PosOrder o : entry.getValue()) {
                    Row row = sheet.createRow(rowIndex++);
                    row.setHeightInPoints(26);

                    String status = o.getOrderStatus() != null ? o.getOrderStatus().toUpperCase() : "PENDING";
                    XSSFCellStyle currentStyle = defaultDataStyle;
                    if ("PENDING".equals(status)) currentStyle = pendingStyle;
                    else if ("SHIPPED".equals(status) || "DELIVERED".equals(status) || "CONFIRMED".equals(status) || "PACKED".equals(status)) currentStyle = greenStyle;
                    else if ("CANCELLED".equals(status)) currentStyle = redStyle;
                    else if ("RETURN".equals(status) || "RETURNED".equals(status) || "RETURN_REQUESTED".equals(status)) currentStyle = purpleStyle;

                    // Items text
                    List<PosOrderItem> items = em.createQuery(
                            "SELECT oi FROM PosOrderItem oi JOIN FETCH oi.productVariant v JOIN FETCH v.product WHERE oi.posOrder.id = :oid",
                            PosOrderItem.class).setParameter("oid", o.getId()).getResultList();
                    StringBuilder itemsSb = new StringBuilder();
                    for (int k = 0; k < items.size(); k++) {
                        if (k > 0) itemsSb.append(", ");
                        PosOrderItem item = items.get(k);
                        itemsSb.append(item.getProductVariant().getProduct().getName()).append(" (x").append(item.getQuantity()).append(")");
                    }

                    // Payment Method checkboxes / indicators
                    String payMethod = o.getPaymentMethod() != null ? o.getPaymentMethod().toUpperCase() : "";
                    String isCod = "COD".equals(payMethod) ? "☑ COD" : "☐";
                    String isAdvCod = "ADVANCE_COD".equals(payMethod) ? "☑ Adv+COD" : "☐";
                    String isFullPaid = "FULL_PAID".equals(payMethod) ? "☑ Full Paid" : "☐";

                    BigDecimal total = o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO;
                    BigDecimal disc = o.getDiscountAmount() != null ? o.getDiscountAmount() : BigDecimal.ZERO;
                    if (!"CANCELLED".equals(status)) {
                        monthTotalSale = monthTotalSale.add(total);
                    }

                    createCell(row, 0, o.getCreatedAt() != null ? o.getCreatedAt().format(dateFmt) : "", currentStyle);
                    createCell(row, 1, "POS-" + String.format("%05d", o.getId()), currentStyle);
                    createCell(row, 2, o.getCustomerName() != null ? o.getCustomerName() : "N/A", currentStyle);
                    createCell(row, 3, itemsSb.toString(), currentStyle);
                    createCell(row, 4, String.format("%,.2f", total), currentStyle);
                    createCell(row, 5, String.format("%,.2f", disc), currentStyle);
                    createCell(row, 6, isCod, currentStyle);
                    createCell(row, 7, isAdvCod, currentStyle);
                    createCell(row, 8, isFullPaid, currentStyle);
                    createCell(row, 9, status, currentStyle);
                    createCell(row, 10, o.getTrackingNumber() != null ? o.getTrackingNumber() : "-", currentStyle);
                }

                // Empty Spacing Row
                rowIndex++;

                // Month Total Summary Row (Large 18pt font)
                Row summaryRow = sheet.createRow(rowIndex);
                summaryRow.setHeightInPoints(36);

                Cell summaryCell = summaryRow.createCell(0);
                summaryCell.setCellValue("Month Total Sale: LKR " + String.format("%,.2f", monthTotalSale));
                summaryCell.setCellStyle(totalStyle);

                // Merge summary row across columns 0 to 10
                sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, 10));

                // Auto-fit column widths
                for (int col = 0; col < headers.length; col++) {
                    sheet.autoSizeColumn(col);
                    int currentWidth = sheet.getColumnWidth(col);
                    sheet.setColumnWidth(col, Math.max(currentWidth + 1200, 5000));
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);

            String filename = "Sales_Report_" + staff.getFullName().replaceAll("\\s+", "_") + ".xlsx";

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
            response.getOutputStream().write(out.toByteArray());
            response.getOutputStream().flush();
        } catch (Exception e) {
            response.setStatus(500);
        }
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
        cell.setCellValue(val);
        cell.setCellStyle(style);
    }
}
