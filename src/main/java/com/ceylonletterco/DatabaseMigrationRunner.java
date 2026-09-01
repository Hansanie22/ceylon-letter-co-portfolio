package com.ceylonletterco;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Runs once on startup to clean up legacy DB columns/tables.
 */
@Component
public class DatabaseMigrationRunner {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void runMigrations() {
        // Drop warehouse_id FK constraint first, then column, then table
        dropForeignKeysOnColumn("inventory", "warehouse_id");
        dropColumnIfExists("inventory", "warehouse_id");
        dropTableIfExists("warehouses");

        // Make addresses.district and addresses.city nullable (optional field)
        makeColumnNullable("addresses", "district", "VARCHAR(100)");
        makeColumnNullable("addresses", "city",     "VARCHAR(100)");
        makeColumnNullable("addresses", "full_name","VARCHAR(100)");

        // Create business_expenses table if not exists
        createBusinessExpensesTable();

        // Create audit_logs table if not exists
        createAuditLogsTable();
    }

    private void createBusinessExpensesTable() {
        try {
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS `business_expenses` (" +
                "  `id`           BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "  `expense_date` DATE           NOT NULL," +
                "  `category`     VARCHAR(50)    NOT NULL," +
                "  `description`  VARCHAR(255)   DEFAULT NULL," +
                "  `amount`       DECIMAL(12,2)  NOT NULL," +
                "  `frequency`    VARCHAR(20)    DEFAULT 'ONE_TIME'," +
                "  `notes`        TEXT           DEFAULT NULL," +
                "  `created_at`   TIMESTAMP      DEFAULT CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
            System.out.println("✅ Migration: business_expenses table ready");
        } catch (Exception e) {
            System.err.println("⚠️ Migration: business_expenses table: " + e.getMessage());
        }
    }

    private void createAuditLogsTable() {
        try {
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS `audit_logs` (" +
                "  `id`           BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "  `user_email`   VARCHAR(150)   DEFAULT 'Guest'," +
                "  `user_role`    VARCHAR(50)    DEFAULT 'GUEST'," +
                "  `action`       VARCHAR(100)   NOT NULL," +
                "  `category`     VARCHAR(50)    NOT NULL," +
                "  `details`      TEXT           DEFAULT NULL," +
                "  `ip_address`   VARCHAR(60)    DEFAULT NULL," +
                "  `user_agent`   VARCHAR(255)   DEFAULT NULL," +
                "  `status`       VARCHAR(20)    DEFAULT 'SUCCESS'," +
                "  `created_at`   TIMESTAMP      DEFAULT CURRENT_TIMESTAMP," +
                "  INDEX `idx_audit_created_at` (`created_at`)," +
                "  INDEX `idx_audit_category`   (`category`)," +
                "  INDEX `idx_audit_user_email` (`user_email`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
            System.out.println("✅ Migration: audit_logs table ready");
        } catch (Exception e) {
            System.err.println("⚠️ Migration: audit_logs table: " + e.getMessage());
        }
    }


    private void dropForeignKeysOnColumn(String table, String column) {
        try {
            // Find all FK constraint names referencing this column
            var constraints = jdbcTemplate.queryForList(
                "SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE " +
                "WHERE TABLE_SCHEMA = DATABASE() " +
                "AND TABLE_NAME = ? AND COLUMN_NAME = ? " +
                "AND REFERENCED_TABLE_NAME IS NOT NULL",
                table, column);

            for (var row : constraints) {
                String constraintName = (String) row.get("CONSTRAINT_NAME");
                try {
                    jdbcTemplate.execute(
                        "ALTER TABLE `" + table + "` DROP FOREIGN KEY `" + constraintName + "`");
                    System.out.println("✅ Migration: dropped FK `" + constraintName + "` from `" + table + "`");
                } catch (Exception ex) {
                    System.err.println("⚠️ Migration: could not drop FK " + constraintName + ": " + ex.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ Migration: FK lookup failed for " + table + "." + column + ": " + e.getMessage());
        }
    }

    private void dropColumnIfExists(String table, String column) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class, table, column);

            if (count != null && count > 0) {
                jdbcTemplate.execute(
                    "ALTER TABLE `" + table + "` DROP COLUMN `" + column + "`");
                System.out.println("✅ Migration: dropped column `" + column + "` from `" + table + "`");
            }
        } catch (Exception e) {
            System.err.println("⚠️ Migration: drop column " + table + "." + column + ": " + e.getMessage());
        }
    }

    private void dropTableIfExists(String table) {
        try {
            jdbcTemplate.execute("DROP TABLE IF EXISTS `" + table + "`");
            System.out.println("✅ Migration: dropped table `" + table + "` (if existed)");
        } catch (Exception e) {
            System.err.println("⚠️ Migration: drop table " + table + ": " + e.getMessage());
        }
    }

    private void makeColumnNullable(String table, String column, String dataType) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ? AND IS_NULLABLE = 'NO'",
                Integer.class, table, column);
            if (count != null && count > 0) {
                jdbcTemplate.execute(
                    "ALTER TABLE `" + table + "` MODIFY COLUMN `" + column + "` " + dataType + " NULL DEFAULT NULL");
                System.out.println("✅ Migration: made `" + table + "`.`" + column + "` nullable");
            }
        } catch (Exception e) {
            System.err.println("⚠️ Migration: makeColumnNullable " + table + "." + column + ": " + e.getMessage());
        }
    }
}
