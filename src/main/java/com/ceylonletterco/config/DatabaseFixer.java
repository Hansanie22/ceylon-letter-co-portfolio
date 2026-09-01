package com.ceylonletterco.config;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseFixer {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseFixer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void fixDatabaseSchema() {
        String[] queries = {
            "ALTER TABLE hero_slide MODIFY COLUMN CREATEDAT DATETIME DEFAULT CURRENT_TIMESTAMP",
            "ALTER TABLE hero_slide MODIFY COLUMN DISPLAYORDER INT DEFAULT 0",
            "ALTER TABLE hero_slide MODIFY COLUMN ISACTIVE TINYINT(1) DEFAULT 1",
            "ALTER TABLE hero_slide MODIFY COLUMN MEDIATYPE VARCHAR(255) DEFAULT 'IMAGE'",
            "ALTER TABLE hero_slide MODIFY COLUMN MEDIAURL VARCHAR(1000) DEFAULT ''",
            "ALTER TABLE hero_slide MODIFY COLUMN ALTTEXT VARCHAR(255) DEFAULT ''",
            "ALTER TABLE inventory MODIFY COLUMN CREATEDAT DATETIME DEFAULT CURRENT_TIMESTAMP",
            "ALTER TABLE inventory MODIFY COLUMN QUANTITYONHAND INT DEFAULT 0",
            "ALTER TABLE inventory MODIFY COLUMN LOWSTOCKTHRESHOLD INT DEFAULT 5",
            "ALTER TABLE inventory MODIFY COLUMN UPDATEDAT DATETIME DEFAULT CURRENT_TIMESTAMP",
            "ALTER TABLE products MODIFY COLUMN CREATEDAT DATETIME DEFAULT CURRENT_TIMESTAMP",
            "ALTER TABLE products MODIFY COLUMN ISACTIVE TINYINT(1) DEFAULT 1",
            "ALTER TABLE categories MODIFY COLUMN ISACTIVE TINYINT(1) DEFAULT 1",
            "ALTER TABLE products MODIFY COLUMN ISDELETED TINYINT(1) DEFAULT 0",
            "ALTER TABLE products MODIFY COLUMN REQUIRESDEPOSIT TINYINT(1) DEFAULT 0",
            "ALTER TABLE products MODIFY COLUMN ISCUSTOMISABLE TINYINT(1) DEFAULT 0",
            "ALTER TABLE products MODIFY COLUMN BASEPRICE DECIMAL(10,2) DEFAULT 0.00",
            "ALTER TABLE product_variants MODIFY COLUMN CREATEDAT DATETIME DEFAULT CURRENT_TIMESTAMP",
            "ALTER TABLE product_variants MODIFY COLUMN ISDELETED TINYINT(1) DEFAULT 0",
            "ALTER TABLE product_variants MODIFY COLUMN COMPAREATPRICE DECIMAL(10,2) DEFAULT 0.00",
            "ALTER TABLE product_variants MODIFY COLUMN METALCOLOR VARCHAR(50) DEFAULT ''",
            "ALTER TABLE product_variants MODIFY COLUMN SIZELENGTH VARCHAR(50) DEFAULT ''",
            "ALTER TABLE product_variants MODIFY COLUMN SKUVARIANT VARCHAR(50) DEFAULT ''",
            "UPDATE users SET role = 'ADMIN', email_verified = 1 WHERE email = 'ceylonletterco@gmail.com'",
            "CREATE TABLE IF NOT EXISTS store_videos ("
            + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
            + "title VARCHAR(255) NOT NULL, "
            + "caption TEXT, "
            + "video_category VARCHAR(50) NOT NULL DEFAULT 'SHOP_THE_LOOK', "
            + "platform VARCHAR(50) NOT NULL DEFAULT 'INSTAGRAM', "
            + "video_url VARCHAR(1000) NOT NULL, "
            + "thumbnail_url VARCHAR(1000), "
            + "product_id INT, "
            + "customer_name VARCHAR(150), "
            + "rating INT DEFAULT 5, "
            + "cta_text VARCHAR(100) DEFAULT 'Shop The Look', "
            + "display_order INT DEFAULT 0, "
            + "is_active TINYINT(1) DEFAULT 1, "
            + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP"
            + ")",
        };
        for (String q : queries) {
            try {
                jdbcTemplate.execute(q);
                System.out.println("✅ Executed: " + q);
            } catch (Exception e) {
                // Ignore silently if column doesn't exist
            }
        }
    }
}
