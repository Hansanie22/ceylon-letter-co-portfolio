package com.ceylonletterco.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * DatabaseInitListener – migrated from the original @WebListener.
 *
 * Runs schema migration ALTER TABLE statements and seed initialization on startup.
 */
@Component
public class DatabaseInitListener {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseInitListener(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void onApplicationReady() {
        runAlter("ALTER TABLE users MODIFY email VARCHAR(100) NULL",
                "DatabaseInitListener: users.email is now NULLABLE.");
        runAlter("ALTER TABLE users ADD COLUMN is_subscribed TINYINT(1) DEFAULT 0 NOT NULL",
                "DatabaseInitListener: Added is_subscribed to users.");
        runAlter("ALTER TABLE users ADD COLUMN auth_provider VARCHAR(50) DEFAULT 'LOCAL' NOT NULL",
                "DatabaseInitListener: Added auth_provider to users.");
        runAlter("ALTER TABLE users ADD COLUMN provider_id VARCHAR(255)",
                "DatabaseInitListener: Added provider_id to users.");
        runAlter("ALTER TABLE products ADD COLUMN requires_deposit TINYINT(1) DEFAULT 0",
                "DatabaseInitListener: Added requires_deposit to products.");
        runAlter("ALTER TABLE product_variants ADD COLUMN is_deleted TINYINT(1) DEFAULT 0 NOT NULL",
                "DatabaseInitListener: Added is_deleted to product_variants.");
        runAlter("ALTER TABLE products ADD COLUMN base_price DECIMAL(10,2) DEFAULT NULL",
                "DatabaseInitListener: Added base_price to products.");

        initStoreVideosTableAndSeeds();
    }

    private void initStoreVideosTableAndSeeds() {
        try {
            String createTableSql = """
                CREATE TABLE IF NOT EXISTS store_videos (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    title VARCHAR(255) NOT NULL,
                    caption TEXT,
                    video_category VARCHAR(50) NOT NULL DEFAULT 'SHOP_THE_LOOK',
                    platform VARCHAR(50) NOT NULL DEFAULT 'INSTAGRAM',
                    video_url VARCHAR(1000) NOT NULL,
                    thumbnail_url VARCHAR(1000),
                    product_id INT NULL,
                    customer_name VARCHAR(150),
                    rating INT DEFAULT 5,
                    cta_text VARCHAR(100) DEFAULT 'Shop The Look',
                    display_order INT NOT NULL DEFAULT 0,
                    is_active TINYINT(1) NOT NULL DEFAULT 1,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT fk_store_videos_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE SET NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """;
            jdbcTemplate.execute(createTableSql);

            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM store_videos", Integer.class);
            if (count != null && count == 0) {
                // Find first few product IDs if available
                Integer p1 = queryFirstProductId();

                String insertSeed = """
                    INSERT INTO store_videos (title, caption, video_category, platform, video_url, thumbnail_url, product_id, customer_name, rating, cta_text, display_order, is_active, created_at)
                    VALUES 
                    (
                        'Signature Letter M in 18K Solid Gold',
                        'Real-life shine and model styling of our bestselling bespoke letter pendant. #CeylonLetterCo #LuxuryJewellery',
                        'SHOP_THE_LOOK',
                        'INSTAGRAM',
                        'https://assets.mixkit.co/videos/preview/mixkit-hands-of-a-woman-putting-on-a-gold-ring-41221-large.mp4',
                        'https://images.unsplash.com/photo-1599643478518-a784e5dc4c8f?w=600&q=80',
                        ?,
                        NULL,
                        5,
                        'Shop This Piece',
                        1,
                        1,
                        NOW()
                    ),
                    (
                        'Velvet Gift Box Unboxing Experience',
                        'Every piece comes sealed with a certificate of authenticity & luxury velvet packaging.',
                        'CUSTOMER_REVIEW',
                        'TIKTOK',
                        'https://assets.mixkit.co/videos/preview/mixkit-holding-a-gold-ring-close-up-41222-large.mp4',
                        'https://images.unsplash.com/photo-1535632066927-ab7c9ab60908?w=600&q=80',
                        ?,
                        'Dinithi Samaraweera',
                        5,
                        'Shop The Look',
                        2,
                        1,
                        NOW()
                    ),
                    (
                        'The Art of Precision Laser Engraving',
                        'Watch our master craftsmen engrave bespoke initials with micrometer accuracy.',
                        'CRAFTSMANSHIP',
                        'YOUTUBE_SHORT',
                        'https://assets.mixkit.co/videos/preview/mixkit-goldsmith-working-on-a-ring-with-a-flame-41220-large.mp4',
                        'https://images.unsplash.com/photo-1605100804763-247f67b3557e?w=600&q=80',
                        ?,
                        NULL,
                        5,
                        'Explore Collection',
                        3,
                        1,
                        NOW()
                    ),
                    (
                        'Rose Gold Initial Necklace Sparkle Test',
                        'Catching natural daylight. 18K Rose gold with diamond pavé accents.',
                        'SHOP_THE_LOOK',
                        'INSTAGRAM',
                        'https://assets.mixkit.co/videos/preview/mixkit-woman-wearing-a-gold-necklace-with-a-pendant-41223-large.mp4',
                        'https://images.unsplash.com/photo-1515562141207-7a88fb7ce338?w=600&q=80',
                        ?,
                        'Ayesha Fernando',
                        5,
                        'Customize Yours',
                        4,
                        1,
                        NOW()
                    );
                """;
                jdbcTemplate.update(insertSeed, p1, p1, p1, p1);
                System.out.println("DatabaseInitListener: Seeded 4 curated luxury store videos into store_videos.");
            }
        } catch (Exception e) {
            System.out.println("DatabaseInitListener: Note on store_videos init: " + e.getMessage());
        }
    }

    private Integer queryFirstProductId() {
        try {
            return jdbcTemplate.queryForObject("SELECT id FROM products WHERE is_active = 1 AND is_deleted = 0 LIMIT 1", Integer.class);
        } catch (Exception e) {
            return null;
        }
    }

    private void runAlter(String sql, String successMsg) {
        try {
            jdbcTemplate.execute(sql);
            System.out.println(successMsg);
        } catch (Exception e) {
            System.out.println("DatabaseInitListener: Skipped (already exists or not needed): " + e.getMessage());
        }
    }
}
