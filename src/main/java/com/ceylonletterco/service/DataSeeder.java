package com.auracraft.service;

import com.auracraft.entity.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Automatically seeds initial categories, products, variants, images,
 * inventory, hero slides, reviews, and admin user for portfolio demonstration.
 */
@Service
public class DataSeeder {

    private static final Logger LOG = Logger.getLogger(DataSeeder.class.getName());

    @PersistenceContext
    private EntityManager em;

    @EventListener(ApplicationReadyEvent.class)
    @Order(100) // Runs after DatabaseMigrationRunner
    @Transactional
    public void seedOnStartup() {
        try {
            Long productCount = em.createQuery("SELECT COUNT(p) FROM Product p WHERE p.isDeleted = false", Long.class)
                    .getSingleResult();

            if (productCount == 0) {
                LOG.info("⚡ Empty database detected. Seeding AuraCraft Studio dummy portfolio data...");
                seedAllData();
                LOG.info("✅ Database successfully seeded with portfolio data & images!");
            } else {
                LOG.info("ℹ️ Database already contains " + productCount + " products. Skipping automatic seed.");
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error checking/seeding database: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void seedAllData() {
        // ── 1. Admin & Customer Users ──────────────────────────────────────────
        createOrUpdateAdmin();
        createOrUpdateCustomer();

        // ── 2. Categories ──────────────────────────────────────────────────────
        Category ringsCat = createCategory("Rings", "Fine handcrafted rings, wedding bands, and statement solitaires.",
                "https://images.unsplash.com/photo-1605100804763-247f67b3557e?w=800&q=80");
        Category necklacesCat = createCategory("Necklaces", "Elegant gold pendants, pearl chokers, and diamond chains.",
                "https://images.unsplash.com/photo-1599643478518-a784e5dc4c8f?w=800&q=80");
        Category braceletsCat = createCategory("Bracelets", "Timeless tennis bracelets, bangles, and delicate cuffs.",
                "https://images.unsplash.com/photo-1611591475168-522f6727cb34?w=800&q=80");
        Category earringsCat = createCategory("Earrings", "Sparkling huggies, baroque pearl drops, and diamond studs.",
                "https://images.unsplash.com/photo-1630019852942-f89202989a59?w=800&q=80");
        Category keepsakesCat = createCategory("Custom Keepsakes", "Bespoke brass wax seals, monogram stamps, and heirloom stationery.",
                "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?w=800&q=80");

        // ── 3. Products with Variants, Images & Inventory ──────────────────────

        // Product 1: Solitaire Diamond Ring
        createProductWithDetails(
                "Aurelia 18K Solitaire Diamond Ring",
                "Handcrafted in 18k solid gold featuring a brilliant certified center diamond. Perfect for engagements and milestones.",
                "RNG-AURELIA-01",
                new BigDecimal("125000.00"),
                ringsCat,
                "AuraCraft Studio",
                "18K Solid Gold with certified 0.75ct brilliant-cut center stone.",
                "Unisex",
                true,
                "Lifetime Craftsmanship Warranty",
                List.of(
                        new VariantData("RNG-AUR-YG-6", "Yellow Gold", "Size 6", new BigDecimal("125000.00"), new BigDecimal("140000.00"), 45),
                        new VariantData("RNG-AUR-YG-7", "Yellow Gold", "Size 7", new BigDecimal("125000.00"), new BigDecimal("140000.00"), 30),
                        new VariantData("RNG-AUR-WG-6", "White Gold", "Size 6", new BigDecimal("128000.00"), new BigDecimal("145000.00"), 25),
                        new VariantData("RNG-AUR-RG-7", "Rose Gold", "Size 7", new BigDecimal("128000.00"), new BigDecimal("145000.00"), 20)
                ),
                List.of(
                        "https://images.unsplash.com/photo-1605100804763-247f67b3557e?w=800&q=80",
                        "https://images.unsplash.com/photo-1603561591411-07134e71a2a9?w=800&q=80",
                        "https://images.unsplash.com/photo-1598560917505-59a3ad559071?w=800&q=80"
                )
        );

        // Product 2: Vintage Signet Ring
        createProductWithDetails(
                "Helios Heirloom Gold Signet Ring",
                "A weighty, classic signet ring made of solid 14k gold with a polished mirror finish. Custom engraving available upon request.",
                "RNG-HELIOS-02",
                new BigDecimal("68000.00"),
                ringsCat,
                "AuraCraft Studio",
                "14K Solid Gold with customizable flat face engraving.",
                "Unisex",
                true,
                "2 Years Warranty",
                List.of(
                        new VariantData("RNG-HEL-YG-8", "Yellow Gold", "Size 8", new BigDecimal("68000.00"), new BigDecimal("75000.00"), 50),
                        new VariantData("RNG-HEL-YG-9", "Yellow Gold", "Size 9", new BigDecimal("68000.00"), new BigDecimal("75000.00"), 40),
                        new VariantData("RNG-HEL-SV-8", "Sterling Silver", "Size 8", new BigDecimal("24000.00"), new BigDecimal("28000.00"), 60)
                ),
                List.of(
                        "https://images.unsplash.com/photo-1603561591411-07134e71a2a9?w=800&q=80",
                        "https://images.unsplash.com/photo-1605100804763-247f67b3557e?w=800&q=80"
                )
        );

        // Product 3: Celestial Pearl Pendant Necklace
        createProductWithDetails(
                "Celeste Freshwater Pearl & Gold Pendant",
                "A luminous baroque freshwater pearl suspended on an exquisite 18k solid gold delicate cable chain.",
                "NCK-CELESTE-01",
                new BigDecimal("42000.00"),
                necklacesCat,
                "AuraCraft Studio",
                "Genuine Baroque Freshwater Pearl with 18k gold bail and chain.",
                "Women",
                false,
                "1 Year Warranty",
                List.of(
                        new VariantData("NCK-CEL-YG-16", "Yellow Gold", "16 inch", new BigDecimal("42000.00"), new BigDecimal("48000.00"), 35),
                        new VariantData("NCK-CEL-YG-18", "Yellow Gold", "18 inch", new BigDecimal("45000.00"), new BigDecimal("52000.00"), 40),
                        new VariantData("NCK-CEL-RG-18", "Rose Gold", "18 inch", new BigDecimal("45000.00"), new BigDecimal("52000.00"), 20)
                ),
                List.of(
                        "https://images.unsplash.com/photo-1599643478518-a784e5dc4c8f?w=800&q=80",
                        "https://images.unsplash.com/photo-1515562141207-7a88fb7ce338?w=800&q=80"
                )
        );

        // Product 4: 14K Gold Herringbone Chain
        createProductWithDetails(
                "Serpentine 14K Italian Herringbone Chain",
                "Fluid, liquid-like drape that catches the light from every angle. Made in Italy with solid 14k gold.",
                "NCK-SERP-02",
                new BigDecimal("85000.00"),
                necklacesCat,
                "AuraCraft Studio",
                "14K Italian Solid Gold Chain (3.5mm width).",
                "Unisex",
                false,
                "2 Years Warranty",
                List.of(
                        new VariantData("NCK-SERP-YG-18", "Yellow Gold", "18 inch", new BigDecimal("85000.00"), new BigDecimal("95000.00"), 30),
                        new VariantData("NCK-SERP-YG-20", "Yellow Gold", "20 inch", new BigDecimal("92000.00"), new BigDecimal("105000.00"), 25),
                        new VariantData("NCK-SERP-WG-18", "White Gold", "18 inch", new BigDecimal("87000.00"), new BigDecimal("98000.00"), 15)
                ),
                List.of(
                        "https://images.unsplash.com/photo-1515562141207-7a88fb7ce338?w=800&q=80",
                        "https://images.unsplash.com/photo-1599643478518-a784e5dc4c8f?w=800&q=80"
                )
        );

        // Product 5: Diamond Tennis Bracelet
        createProductWithDetails(
                "Eternity Diamond Tennis Bracelet",
                "A breathtaking continuous line of 55 round brilliant diamonds set in four-prong 18k white or yellow gold.",
                "BRC-ETERN-01",
                new BigDecimal("185000.00"),
                braceletsCat,
                "AuraCraft Studio",
                "18K Gold with 2.50ct Total Diamond Weight (VS-SI Clarity).",
                "Women",
                false,
                "Lifetime Guarantee",
                List.of(
                        new VariantData("BRC-ET-WG-7", "White Gold", "7 inch", new BigDecimal("185000.00"), new BigDecimal("210000.00"), 15),
                        new VariantData("BRC-ET-YG-7", "Yellow Gold", "7 inch", new BigDecimal("185000.00"), new BigDecimal("210000.00"), 20)
                ),
                List.of(
                        "https://images.unsplash.com/photo-1611591475168-522f6727cb34?w=800&q=80",
                        "https://images.unsplash.com/photo-1535632066927-ab7c9ab60908?w=800&q=80"
                )
        );

        // Product 6: Handcrafted Gold Bangle
        createProductWithDetails(
                "Aura Hammered Gold Cuff Bangle",
                "Textured by hand with a soft organic artisan finish. Beautiful worn alone or stacked with other bracelets.",
                "BRC-AURA-02",
                new BigDecimal("54000.00"),
                braceletsCat,
                "AuraCraft Studio",
                "18K Gold Plated Vermeil over Solid Sterling Silver.",
                "Women",
                false,
                "1 Year Warranty",
                List.of(
                        new VariantData("BRC-AUR-YG-M", "Yellow Gold", "Medium", new BigDecimal("54000.00"), new BigDecimal("62000.00"), 40),
                        new VariantData("BRC-AUR-SV-M", "Sterling Silver", "Medium", new BigDecimal("32000.00"), new BigDecimal("38000.00"), 50)
                ),
                List.of(
                        "https://images.unsplash.com/photo-1535632066927-ab7c9ab60908?w=800&q=80",
                        "https://images.unsplash.com/photo-1602751584552-8ba73aad10e1?w=800&q=80"
                )
        );

        // Product 7: Baroque Pearl Drop Earrings
        createProductWithDetails(
                "Lumière Baroque Pearl Drop Earrings",
                "One-of-a-kind organic baroque pearls gracefully suspended from 14k gold huggie hoops.",
                "EAR-LUMI-01",
                new BigDecimal("38000.00"),
                earringsCat,
                "AuraCraft Studio",
                "Natural Baroque Pearls with 14K Gold Hoops.",
                "Women",
                false,
                "1 Year Warranty",
                List.of(
                        new VariantData("EAR-LUM-YG-STD", "Yellow Gold", "Standard", new BigDecimal("38000.00"), new BigDecimal("44000.00"), 35),
                        new VariantData("EAR-LUM-WG-STD", "White Gold", "Standard", new BigDecimal("38000.00"), new BigDecimal("44000.00"), 25)
                ),
                List.of(
                        "https://images.unsplash.com/photo-1630019852942-f89202989a59?w=800&q=80",
                        "https://images.unsplash.com/photo-1635767798638-3e25273a8236?w=800&q=80"
                )
        );

        // Product 8: Custom Brass Wax Seal Kit
        createProductWithDetails(
                "Royal Monogram Custom Brass Wax Seal Kit",
                "Precision deep-engraved solid brass seal stamp paired with a polished rosewood handle, melting spoon, and sealing wax beads.",
                "KPS-WAX-01",
                new BigDecimal("12500.00"),
                keepsakesCat,
                "AuraCraft Studio",
                "Custom Engraved Brass Die with Premium Rosewood Handle & Wax Kit.",
                "Unisex",
                true,
                "Lifetime Stamp Warranty",
                List.of(
                        new VariantData("KPS-WAX-BRASS-25MM", "Antique Brass", "25mm Round", new BigDecimal("12500.00"), new BigDecimal("15000.00"), 100),
                        new VariantData("KPS-WAX-GOLD-30MM", "Polished Gold", "30mm Round", new BigDecimal("14500.00"), new BigDecimal("17000.00"), 75)
                ),
                List.of(
                        "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?w=800&q=80",
                        "https://images.unsplash.com/photo-1586075010923-2dd4570fb338?w=800&q=80"
                )
        );

        // ── 4. Hero Slides ─────────────────────────────────────────────────────
        createHeroSlides();

        // ── 5. Store Videos / Social Reels ─────────────────────────────────────
        createStoreVideos();
    }

    private void createOrUpdateAdmin() {
        try {
            List<User> existing = em.createQuery("SELECT u FROM User u WHERE u.email = :email", User.class)
                    .setParameter("email", "admin@auracraft.com")
                    .getResultList();
            if (existing.isEmpty()) {
                User admin = new User();
                admin.setEmail("admin@auracraft.com");
                admin.setPassword(BCrypt.hashpw("1234", BCrypt.gensalt(12)));
                admin.setFullName("AuraCraft Studio Admin");
                admin.setRole("ADMIN");
                admin.setEmailVerified(true);
                admin.setPhone("+94771234567");
                em.persist(admin);
            }
        } catch (Exception e) {
            LOG.warning("Could not create admin: " + e.getMessage());
        }
    }

    private void createOrUpdateCustomer() {
        try {
            List<User> existing = em.createQuery("SELECT u FROM User u WHERE u.email = :email", User.class)
                    .setParameter("email", "customer@gmail.com")
                    .getResultList();
            if (existing.isEmpty()) {
                User user = new User();
                user.setEmail("customer@gmail.com");
                user.setPassword(BCrypt.hashpw("1234", BCrypt.gensalt(12)));
                user.setFullName("Hansanie Prabodha");
                user.setRole("CUSTOMER");
                user.setEmailVerified(true);
                user.setPhone("+94719876543");
                em.persist(user);
            }
        } catch (Exception e) {
            LOG.warning("Could not create customer: " + e.getMessage());
        }
    }

    private Category createCategory(String name, String description, String imageUrl) {
        List<Category> existing = em.createQuery("SELECT c FROM Category c WHERE c.name = :name", Category.class)
                .setParameter("name", name).getResultList();
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        Category cat = new Category();
        cat.setName(name);
        cat.setDescription(description);
        cat.setImageUrl(imageUrl);
        cat.setIsActive(true);
        em.persist(cat);
        return cat;
    }

    private void createProductWithDetails(
            String name,
            String description,
            String sku,
            BigDecimal basePrice,
            Category category,
            String brand,
            String summary,
            String gender,
            boolean isCustomisable,
            String warrantyPeriod,
            List<VariantData> variants,
            List<String> imageUrls
    ) {
        Product p = new Product();
        p.setName(name);
        p.setDescription(description);
        p.setSku(sku);
        p.setBasePrice(basePrice);
        p.setCategory(category);
        p.setBrand(brand);
        p.setSummary(summary);
        p.setGender(gender);
        p.setIsCustomisable(isCustomisable);
        p.setWarrantyPeriod(warrantyPeriod);
        p.setAvailabilityStatus("IN_STOCK");
        p.setIsActive(true);
        p.setIsDeleted(false);
        em.persist(p);

        // Images
        for (int i = 0; i < imageUrls.size(); i++) {
            ProductImage img = new ProductImage();
            img.setProduct(p);
            img.setImageUrl(imageUrls.get(i));
            img.setIsPrimary(i == 0);
            img.setSortOrder(i);
            em.persist(img);
        }

        // Variants and Inventory
        for (VariantData vd : variants) {
            ProductVariant v = new ProductVariant();
            v.setProduct(p);
            v.setSkuVariant(vd.sku);
            v.setMetalColor(vd.color);
            v.setSizeLength(vd.size);
            v.setPrice(vd.price);
            v.setCompareAtPrice(vd.compareAtPrice);
            v.setCostPrice(vd.price.multiply(new BigDecimal("0.6"))); // 40% margin
            v.setIsDeleted(false);
            em.persist(v);

            Inventory inv = new Inventory();
            inv.setProductVariant(v);
            inv.setVariantId(v.getId());
            inv.setQuantityOnHand(vd.stock);
            inv.setLowStockThreshold(5);
            em.persist(inv);
        }
    }

    private void createHeroSlides() {
        try {
            Long count = em.createQuery("SELECT COUNT(h) FROM HeroSlide h", Long.class).getSingleResult();
            if (count > 0) return;

            HeroSlide s1 = new HeroSlide();
            s1.setHeading("Timeless Heritage, Modern Grace");
            s1.setDescription("Discover handcrafted fine jewellery sculpted with ethical gemstones and pure gold.");
            s1.setTag("THE HEIRLOOM COLLECTION");
            s1.setMediaType("IMAGE");
            s1.setMediaUrl("https://images.unsplash.com/photo-1515562141207-7a88fb7ce338?w=1600&q=85");
            s1.setAltText("AuraCraft Studio Fine Jewellery Collection");
            s1.setDisplayOrder(1);
            s1.setActive(true);
            em.persist(s1);

            HeroSlide s2 = new HeroSlide();
            s2.setHeading("Bespoke Bridal & Solitaires");
            s2.setDescription("Handcrafted engagement rings and wedding bands designed to celebrate the moments that define a lifetime.");
            s2.setTag("BRIDAL EXCLUSIVES");
            s2.setMediaType("IMAGE");
            s2.setMediaUrl("https://images.unsplash.com/photo-1605100804763-247f67b3557e?w=1600&q=85");
            s2.setAltText("Solitaire Diamond Engagement Ring");
            s2.setDisplayOrder(2);
            s2.setActive(true);
            em.persist(s2);

            HeroSlide s3 = new HeroSlide();
            s3.setHeading("Artisan Keepsakes & Wax Seals");
            s3.setDescription("Personalized brass wax stamps, monogram signets, and bespoke stationery crafted for heirloom letter-writing.");
            s3.setTag("CUSTOM CRAFTSMANSHIP");
            s3.setMediaType("IMAGE");
            s3.setMediaUrl("https://images.unsplash.com/photo-1579783902614-a3fb3927b675?w=1600&q=85");
            s3.setAltText("Artisan Custom Wax Seal Stamp");
            s3.setDisplayOrder(3);
            s3.setActive(true);
            em.persist(s3);
        } catch (Exception e) {
            LOG.warning("Could not seed hero slides: " + e.getMessage());
        }
    }

    private void createStoreVideos() {
        try {
            Long count = em.createQuery("SELECT COUNT(v) FROM StoreVideo v", Long.class).getSingleResult();
            if (count > 0) return;

            StoreVideo v1 = new StoreVideo();
            v1.setTitle("Master Goldsmith Polishing Solitaire Ring");
            v1.setCaption("Every angle finished with precision and timeless beauty in our Colombo workshop.");
            v1.setVideoCategory("CRAFTSMANSHIP");
            v1.setPlatform("DIRECT_URL");
            v1.setVideoUrl("https://assets.mixkit.co/videos/preview/mixkit-hands-of-a-jeweler-polishing-a-ring-41617-large.mp4");
            v1.setThumbnailUrl("https://images.unsplash.com/photo-1605100804763-247f67b3557e?w=600&q=80");
            v1.setCustomerName("Master Artisan");
            v1.setRating(5);
            v1.setCtaText("View Rings");
            v1.setDisplayOrder(1);
            v1.setActive(true);
            em.persist(v1);

            StoreVideo v2 = new StoreVideo();
            v2.setTitle("Unboxing The Celeste Pearl Pendant");
            v2.setCaption("Complimentary velvet box, certification card, and gold wax ribbon with every piece.");
            v2.setVideoCategory("SHOP_THE_LOOK");
            v2.setPlatform("DIRECT_URL");
            v2.setVideoUrl("https://assets.mixkit.co/videos/preview/mixkit-putting-a-shiny-ring-into-a-gift-box-41620-large.mp4");
            v2.setThumbnailUrl("https://images.unsplash.com/photo-1599643478518-a784e5dc4c8f?w=600&q=80");
            v2.setCustomerName("Amara Perera");
            v2.setRating(5);
            v2.setCtaText("Shop Celeste");
            v2.setDisplayOrder(2);
            v2.setActive(true);
            em.persist(v2);
        } catch (Exception e) {
            LOG.warning("Could not seed store videos: " + e.getMessage());
        }
    }

    private static class VariantData {
        String sku;
        String color;
        String size;
        BigDecimal price;
        BigDecimal compareAtPrice;
        int stock;

        public VariantData(String sku, String color, String size, BigDecimal price, BigDecimal compareAtPrice, int stock) {
            this.sku = sku;
            this.color = color;
            this.size = size;
            this.price = price;
            this.compareAtPrice = compareAtPrice;
            this.stock = stock;
        }
    }
}
