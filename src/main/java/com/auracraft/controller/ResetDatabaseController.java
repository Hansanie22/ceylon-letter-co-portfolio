package com.auracraft.controller;

import com.auracraft.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ResetDatabaseController {

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private com.auracraft.service.DataSeeder dataSeeder;

    @GetMapping("/api/seed-database")
    @Transactional
    public String seedDatabase() {
        try {
            dataSeeder.seedAllData();
            return "Database successfully populated with dummy products, categories, variants, images, hero slides, and admin!";
        } catch (Exception e) {
            return "Failed to seed database: " + e.getMessage();
        }
    }

    @GetMapping("/api/nuke-and-seed")
    @Transactional
    public String nukeAndSeed() {
        nukeDatabase();
        try {
            dataSeeder.seedAllData();
            return "Database nuked and freshly populated with all portfolio dummy data!";
        } catch (Exception e) {
            return "Nuked, but failed to seed: " + e.getMessage();
        }
    }

    @GetMapping("/api/nuke-database-confirm")
    @Transactional
    public String nukeDatabase() {
        try {
            em.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate();
            
            String[] tables = {
                "support_messages", "support_tickets", "notifications", "wishlist_items",
                "order_items", "payments", "orders", "addresses", "cart_items",
                "inventory", "product_images", "product_variants", "products",
                "categories", "reviews", "discount_subscriptions", "users", "hero_slide",
                "store_videos", "store_video_variants", "business_expenses", "audit_logs"
            };
            
            for (String table : tables) {
                try {
                    em.createNativeQuery("TRUNCATE TABLE " + table).executeUpdate();
                } catch (Exception ignored) {}
            }
            
            em.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate();

            User admin = new User();
            admin.setEmail("admin@auracraft.com");
            admin.setPassword(BCrypt.hashpw("1234", BCrypt.gensalt(12)));
            admin.setFullName("Admin");
            admin.setRole("ADMIN");
            admin.setEmailVerified(true);
            em.persist(admin);

            return "Database cleared successfully! Admin user created with email admin@auracraft.com and password 1234.";
        } catch (Exception e) {
            return "Failed: " + e.getMessage();
        }
    }

    @GetMapping("/api/reset-orders")
    @Transactional
    public String resetOrders() {
        try {
            em.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate();
            
            String[] tables = {
                "order_items", "orders", "payments", "pos_order_items", "pos_orders", "sales_rep_requests"
            };
            
            for (String table : tables) {
                try {
                    em.createNativeQuery("TRUNCATE TABLE " + table).executeUpdate();
                } catch (Exception ignored) {}
            }
            
            em.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate();

            return "Order and sales data cleared successfully!";
        } catch (Exception e) {
            return "Failed: " + e.getMessage();
        }
    }
}
