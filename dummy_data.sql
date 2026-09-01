-- ═══════════════════════════════════════════════════════════════════════
-- AuraCraft Studio - Full Database Schema & Dummy Portfolio Data
-- Target: TiDB Cloud / MySQL 8.0+
-- ═══════════════════════════════════════════════════════════════════════

-- 1. CREATE & USE DATABASE
CREATE DATABASE IF NOT EXISTS `auracraft` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `auracraft`;

SET FOREIGN_KEY_CHECKS = 0;

-- 2. DROP EXISTING TABLES IF ANY
DROP TABLE IF EXISTS `support_messages`;
DROP TABLE IF EXISTS `support_tickets`;
DROP TABLE IF EXISTS `notifications`;
DROP TABLE IF EXISTS `wishlist_items`;
DROP TABLE IF EXISTS `cart_items`;
DROP TABLE IF EXISTS `payments`;
DROP TABLE IF EXISTS `order_items`;
DROP TABLE IF EXISTS `orders`;
DROP TABLE IF EXISTS `addresses`;
DROP TABLE IF EXISTS `reviews`;
DROP TABLE IF EXISTS `store_video_variants`;
DROP TABLE IF EXISTS `store_videos`;
DROP TABLE IF EXISTS `hero_slide`;
DROP TABLE IF EXISTS `inventory`;
DROP TABLE IF EXISTS `product_images`;
DROP TABLE IF EXISTS `product_variants`;
DROP TABLE IF EXISTS `products`;
DROP TABLE IF EXISTS `categories`;
DROP TABLE IF EXISTS `users`;
DROP TABLE IF EXISTS `business_expenses`;
DROP TABLE IF EXISTS `audit_logs`;

-- 3. CREATE TABLES

CREATE TABLE `users` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `email` VARCHAR(100) UNIQUE,
  `password` VARCHAR(255),
  `full_name` VARCHAR(100) NOT NULL,
  `phone` VARCHAR(15) DEFAULT NULL,
  `date_of_birth` DATE DEFAULT NULL,
  `profile_image_url` VARCHAR(512) DEFAULT NULL,
  `role` VARCHAR(255) NOT NULL DEFAULT 'CUSTOMER',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `email_verified` TINYINT(1) NOT NULL DEFAULT 0,
  `verification_token` VARCHAR(64) UNIQUE DEFAULT NULL,
  `verification_token_expiry` DATETIME DEFAULT NULL,
  `password_reset_token` VARCHAR(64) UNIQUE DEFAULT NULL,
  `password_reset_token_expiry` DATETIME DEFAULT NULL,
  `is_subscribed` TINYINT(1) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `categories` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `name` VARCHAR(100) NOT NULL UNIQUE,
  `description` VARCHAR(255) DEFAULT NULL,
  `image_url` VARCHAR(512) DEFAULT NULL,
  `is_active` TINYINT(1) DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `products` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `name` VARCHAR(200) NOT NULL,
  `description` TEXT DEFAULT NULL,
  `sku` VARCHAR(50) NOT NULL,
  `base_price` DECIMAL(10,2) DEFAULT NULL,
  `category_id` INT DEFAULT NULL,
  `is_active` TINYINT(1) DEFAULT 1,
  `requires_deposit` TINYINT(1) DEFAULT 0,
  `brand` VARCHAR(100) DEFAULT NULL,
  `summary` TEXT DEFAULT NULL,
  `gender` VARCHAR(20) DEFAULT NULL,
  `is_customisable` TINYINT(1) DEFAULT 0,
  `availability_status` VARCHAR(30) DEFAULT 'IN_STOCK',
  `warranty_period` VARCHAR(50) DEFAULT NULL,
  `is_deleted` TINYINT(1) DEFAULT 0,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (`category_id`) REFERENCES `categories`(`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `product_variants` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `product_id` INT NOT NULL,
  `sku_variant` VARCHAR(50) NOT NULL UNIQUE,
  `metal_color` VARCHAR(50) DEFAULT NULL,
  `size_length` VARCHAR(50) DEFAULT NULL,
  `price` DECIMAL(10,2) NOT NULL,
  `compare_at_price` DECIMAL(10,2) DEFAULT NULL,
  `cost_price` DECIMAL(10,2) DEFAULT 0.00,
  `is_deleted` TINYINT(1) DEFAULT 0,
  FOREIGN KEY (`product_id`) REFERENCES `products`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `inventory` (
  `variant_id` INT PRIMARY KEY,
  `quantity_on_hand` INT NOT NULL DEFAULT 0,
  `low_stock_threshold` INT DEFAULT 5,
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (`variant_id`) REFERENCES `product_variants`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `product_images` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `product_id` INT NOT NULL,
  `image_url` VARCHAR(512) NOT NULL,
  `is_primary` TINYINT(1) DEFAULT 0,
  `sort_order` INT DEFAULT 0,
  FOREIGN KEY (`product_id`) REFERENCES `products`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `hero_slide` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `heading` VARCHAR(255) DEFAULT NULL,
  `description` VARCHAR(1000) DEFAULT NULL,
  `tag` VARCHAR(100) DEFAULT NULL,
  `media_type` VARCHAR(10) NOT NULL DEFAULT 'IMAGE',
  `media_url` VARCHAR(1000) NOT NULL,
  `alt_text` VARCHAR(255) DEFAULT NULL,
  `display_order` INT NOT NULL DEFAULT 0,
  `is_active` TINYINT(1) NOT NULL DEFAULT 1,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `store_videos` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `title` VARCHAR(255) NOT NULL,
  `caption` TEXT DEFAULT NULL,
  `video_category` VARCHAR(50) NOT NULL DEFAULT 'SHOP_THE_LOOK',
  `platform` VARCHAR(50) NOT NULL DEFAULT 'INSTAGRAM',
  `video_url` VARCHAR(1000) NOT NULL,
  `thumbnail_url` VARCHAR(1000) DEFAULT NULL,
  `product_id` INT DEFAULT NULL,
  `customer_name` VARCHAR(150) DEFAULT NULL,
  `rating` INT DEFAULT 5,
  `cta_text` VARCHAR(100) DEFAULT 'Shop The Look',
  `display_order` INT NOT NULL DEFAULT 0,
  `is_active` TINYINT(1) NOT NULL DEFAULT 1,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (`product_id`) REFERENCES `products`(`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `store_video_variants` (
  `video_id` BIGINT NOT NULL,
  `variant_id` INT NOT NULL,
  PRIMARY KEY (`video_id`, `variant_id`),
  FOREIGN KEY (`video_id`) REFERENCES `store_videos`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`variant_id`) REFERENCES `product_variants`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `reviews` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `variant_id` INT NOT NULL,
  `user_id` INT NOT NULL,
  `rating` INT NOT NULL,
  `comment` TEXT DEFAULT NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (`variant_id`) REFERENCES `product_variants`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `addresses` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `user_id` INT DEFAULT NULL,
  `full_name` VARCHAR(100) DEFAULT NULL,
  `phone` VARCHAR(20) DEFAULT NULL,
  `address_line1` VARCHAR(255) DEFAULT NULL,
  `address_line2` VARCHAR(255) DEFAULT NULL,
  `city` VARCHAR(100) DEFAULT NULL,
  `district` VARCHAR(100) DEFAULT NULL,
  `postal_code` VARCHAR(20) DEFAULT NULL,
  `country` VARCHAR(50) DEFAULT 'Sri Lanka',
  `is_default` TINYINT(1) DEFAULT 0,
  FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `orders` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `user_id` INT DEFAULT NULL,
  `order_number` VARCHAR(50) UNIQUE NOT NULL,
  `total_amount` DECIMAL(10,2) NOT NULL,
  `subtotal` DECIMAL(10,2) NOT NULL,
  `shipping_cost` DECIMAL(10,2) DEFAULT 350.00,
  `discount_amount` DECIMAL(10,2) DEFAULT 0.00,
  `order_status` VARCHAR(50) DEFAULT 'PENDING',
  `payment_status` VARCHAR(50) DEFAULT 'UNPAID',
  `shipping_address_id` INT DEFAULT NULL,
  `notes` TEXT DEFAULT NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `order_items` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `order_id` INT NOT NULL,
  `variant_id` INT NOT NULL,
  `quantity` INT NOT NULL,
  `price` DECIMAL(10,2) NOT NULL,
  `total_price` DECIMAL(10,2) NOT NULL,
  FOREIGN KEY (`order_id`) REFERENCES `orders`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`variant_id`) REFERENCES `product_variants`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `payments` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `order_id` INT NOT NULL,
  `payment_method` VARCHAR(50) DEFAULT 'CARD',
  `amount` DECIMAL(10,2) NOT NULL,
  `payment_status` VARCHAR(50) DEFAULT 'PENDING',
  `transaction_id` VARCHAR(100) DEFAULT NULL,
  `paid_at` DATETIME DEFAULT NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (`order_id`) REFERENCES `orders`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `cart_items` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `user_id` INT NOT NULL,
  `variant_id` INT NOT NULL,
  `quantity` INT NOT NULL DEFAULT 1,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`variant_id`) REFERENCES `product_variants`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `wishlist_items` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `user_id` INT NOT NULL,
  `variant_id` INT NOT NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`variant_id`) REFERENCES `product_variants`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `notifications` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `user_id` INT NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `message` TEXT NOT NULL,
  `type` VARCHAR(50) DEFAULT 'INFO',
  `is_read` TINYINT(1) DEFAULT 0,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `support_tickets` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `user_id` INT DEFAULT NULL,
  `subject` VARCHAR(255) NOT NULL,
  `status` VARCHAR(50) DEFAULT 'OPEN',
  `priority` VARCHAR(50) DEFAULT 'NORMAL',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `support_messages` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `ticket_id` INT NOT NULL,
  `sender_type` VARCHAR(50) NOT NULL,
  `message` TEXT NOT NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (`ticket_id`) REFERENCES `support_tickets`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `business_expenses` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `expense_date` DATE NOT NULL,
  `category` VARCHAR(50) NOT NULL,
  `description` VARCHAR(255) DEFAULT NULL,
  `amount` DECIMAL(12,2) NOT NULL,
  `frequency` VARCHAR(20) DEFAULT 'ONE_TIME',
  `notes` TEXT DEFAULT NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `audit_logs` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_email` VARCHAR(150) DEFAULT 'Guest',
  `user_role` VARCHAR(50) DEFAULT 'GUEST',
  `action` VARCHAR(100) NOT NULL,
  `category` VARCHAR(50) NOT NULL,
  `details` TEXT DEFAULT NULL,
  `ip_address` VARCHAR(60) DEFAULT NULL,
  `user_agent` VARCHAR(255) DEFAULT NULL,
  `status` VARCHAR(20) DEFAULT 'SUCCESS',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_audit_created_at` (`created_at`),
  INDEX `idx_audit_category` (`category`),
  INDEX `idx_audit_user_email` (`user_email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. INSERT SEED DATA

-- USERS (Admin & Customer - password is 1234)
INSERT INTO `users` (`id`, `email`, `password`, `full_name`, `phone`, `role`, `email_verified`) VALUES
(1, 'admin@auracraft.com', '$2a$12$e6o1jG1tV7rT2rC/aFf36Oq7N5g81eB8x7L0sT.JjKk8xO4b2g9e6', 'AuraCraft Studio Admin', '+94771234567', 'ADMIN', 1),
(2, 'customer@gmail.com', '$2a$12$e6o1jG1tV7rT2rC/aFf36Oq7N5g81eB8x7L0sT.JjKk8xO4b2g9e6', 'Hansanie Prabodha', '+94719876543', 'CUSTOMER', 1);

-- CATEGORIES
INSERT INTO `categories` (`id`, `name`, `description`, `image_url`, `is_active`) VALUES
(1, 'Rings', 'Fine handcrafted rings, wedding bands, and statement solitaires.', 'https://images.unsplash.com/photo-1605100804763-247f67b3557e?w=800&q=80', 1),
(2, 'Necklaces', 'Elegant gold pendants, pearl chokers, and diamond chains.', 'https://images.unsplash.com/photo-1599643478518-a784e5dc4c8f?w=800&q=80', 1),
(3, 'Bracelets', 'Timeless tennis bracelets, bangles, and delicate cuffs.', 'https://images.unsplash.com/photo-1611591475168-522f6727cb34?w=800&q=80', 1),
(4, 'Earrings', 'Sparkling huggies, baroque pearl drops, and diamond studs.', 'https://images.unsplash.com/photo-1630019852942-f89202989a59?w=800&q=80', 1),
(5, 'Custom Keepsakes', 'Bespoke brass wax seals, monogram stamps, and heirloom stationery.', 'https://images.unsplash.com/photo-1579783902614-a3fb3927b675?w=800&q=80', 1);

-- PRODUCTS
INSERT INTO `products` (`id`, `name`, `description`, `sku`, `base_price`, `category_id`, `is_active`, `requires_deposit`, `brand`, `summary`, `gender`, `is_customisable`, `availability_status`, `warranty_period`, `is_deleted`) VALUES
(1, 'Aurelia 18K Solitaire Diamond Ring', 'Handcrafted in 18k solid gold featuring a brilliant certified center diamond. Perfect for engagements and milestones.', 'RNG-AURELIA-01', 125000.00, 1, 1, 0, 'AuraCraft Studio', '18K Solid Gold with certified 0.75ct brilliant-cut center stone.', 'Unisex', 1, 'IN_STOCK', 'Lifetime Craftsmanship Warranty', 0),
(2, 'Helios Heirloom Gold Signet Ring', 'A weighty, classic signet ring made of solid 14k gold with a polished mirror finish. Custom engraving available upon request.', 'RNG-HELIOS-02', 68000.00, 1, 1, 0, 'AuraCraft Studio', '14K Solid Gold with customizable flat face engraving.', 'Unisex', 1, 'IN_STOCK', '2 Years Warranty', 0),
(3, 'Celeste Freshwater Pearl & Gold Pendant', 'A luminous baroque freshwater pearl suspended on an exquisite 18k solid gold delicate cable chain.', 'NCK-CELESTE-01', 42000.00, 2, 1, 0, 'AuraCraft Studio', 'Genuine Baroque Freshwater Pearl with 18k gold bail and chain.', 'Women', 0, 'IN_STOCK', '1 Year Warranty', 0),
(4, 'Serpentine 14K Italian Herringbone Chain', 'Fluid, liquid-like drape that catches the light from every angle. Made in Italy with solid 14k gold.', 'NCK-SERP-02', 85000.00, 2, 1, 0, 'AuraCraft Studio', '14K Italian Solid Gold Chain (3.5mm width).', 'Unisex', 0, 'IN_STOCK', '2 Years Warranty', 0),
(5, 'Eternity Diamond Tennis Bracelet', 'A breathtaking continuous line of 55 round brilliant diamonds set in four-prong 18k white or yellow gold.', 'BRC-ETERN-01', 185000.00, 3, 1, 0, 'AuraCraft Studio', '18K Gold with 2.50ct Total Diamond Weight (VS-SI Clarity).', 'Women', 0, 'IN_STOCK', 'Lifetime Guarantee', 0),
(6, 'Aura Hammered Gold Cuff Bangle', 'Textured by hand with a soft organic artisan finish. Beautiful worn alone or stacked with other bracelets.', 'BRC-AURA-02', 54000.00, 3, 1, 0, 'AuraCraft Studio', '18K Gold Plated Vermeil over Solid Sterling Silver.', 'Women', 0, 'IN_STOCK', '1 Year Warranty', 0),
(7, 'Lumière Baroque Pearl Drop Earrings', 'One-of-a-kind organic baroque pearls gracefully suspended from 14k gold huggie hoops.', 'EAR-LUMI-01', 38000.00, 4, 1, 0, 'AuraCraft Studio', 'Natural Baroque Pearls with 14K Gold Hoops.', 'Women', 0, 'IN_STOCK', '1 Year Warranty', 0),
(8, 'Royal Monogram Custom Brass Wax Seal Kit', 'Precision deep-engraved solid brass seal stamp paired with a polished rosewood handle, melting spoon, and sealing wax beads.', 'KPS-WAX-01', 12500.00, 5, 1, 0, 'AuraCraft Studio', 'Custom Engraved Brass Die with Premium Rosewood Handle & Wax Kit.', 'Unisex', 1, 'IN_STOCK', 'Lifetime Stamp Warranty', 0);

-- PRODUCT VARIANTS
INSERT INTO `product_variants` (`id`, `product_id`, `sku_variant`, `metal_color`, `size_length`, `price`, `compare_at_price`, `cost_price`, `is_deleted`) VALUES
(1, 1, 'RNG-AUR-YG-6', 'Yellow Gold', 'Size 6', 125000.00, 140000.00, 75000.00, 0),
(2, 1, 'RNG-AUR-YG-7', 'Yellow Gold', 'Size 7', 125000.00, 140000.00, 75000.00, 0),
(3, 1, 'RNG-AUR-WG-6', 'White Gold', 'Size 6', 128000.00, 145000.00, 77000.00, 0),
(4, 2, 'RNG-HEL-YG-8', 'Yellow Gold', 'Size 8', 68000.00, 75000.00, 40000.00, 0),
(5, 2, 'RNG-HEL-SV-8', 'Sterling Silver', 'Size 8', 24000.00, 28000.00, 14000.00, 0),
(6, 3, 'NCK-CEL-YG-16', 'Yellow Gold', '16 inch', 42000.00, 48000.00, 25000.00, 0),
(7, 3, 'NCK-CEL-YG-18', 'Yellow Gold', '18 inch', 45000.00, 52000.00, 27000.00, 0),
(8, 4, 'NCK-SERP-YG-18', 'Yellow Gold', '18 inch', 85000.00, 95000.00, 51000.00, 0),
(9, 5, 'BRC-ET-WG-7', 'White Gold', '7 inch', 185000.00, 210000.00, 110000.00, 0),
(10, 6, 'BRC-AUR-YG-M', 'Yellow Gold', 'Medium', 54000.00, 62000.00, 32000.00, 0),
(11, 7, 'EAR-LUM-YG-STD', 'Yellow Gold', 'Standard', 38000.00, 44000.00, 22000.00, 0),
(12, 8, 'KPS-WAX-BRASS-25MM', 'Antique Brass', '25mm Round', 12500.00, 15000.00, 7000.00, 0);

-- INVENTORY
INSERT INTO `inventory` (`variant_id`, `quantity_on_hand`, `low_stock_threshold`) VALUES
(1, 45, 5), (2, 30, 5), (3, 25, 5), (4, 50, 5),
(5, 60, 5), (6, 35, 5), (7, 40, 5), (8, 30, 5),
(9, 15, 5), (10, 40, 5), (11, 35, 5), (12, 100, 5);

-- PRODUCT IMAGES
INSERT INTO `product_images` (`id`, `product_id`, `image_url`, `is_primary`, `sort_order`) VALUES
(1, 1, 'https://images.unsplash.com/photo-1605100804763-247f67b3557e?w=800&q=80', 1, 0),
(2, 1, 'https://images.unsplash.com/photo-1603561591411-07134e71a2a9?w=800&q=80', 0, 1),
(3, 2, 'https://images.unsplash.com/photo-1603561591411-07134e71a2a9?w=800&q=80', 1, 0),
(4, 3, 'https://images.unsplash.com/photo-1599643478518-a784e5dc4c8f?w=800&q=80', 1, 0),
(5, 3, 'https://images.unsplash.com/photo-1515562141207-7a88fb7ce338?w=800&q=80', 0, 1),
(6, 4, 'https://images.unsplash.com/photo-1515562141207-7a88fb7ce338?w=800&q=80', 1, 0),
(7, 5, 'https://images.unsplash.com/photo-1611591475168-522f6727cb34?w=800&q=80', 1, 0),
(8, 6, 'https://images.unsplash.com/photo-1535632066927-ab7c9ab60908?w=800&q=80', 1, 0),
(9, 7, 'https://images.unsplash.com/photo-1630019852942-f89202989a59?w=800&q=80', 1, 0),
(10, 8, 'https://images.unsplash.com/photo-1579783902614-a3fb3927b675?w=800&q=80', 1, 0),
(11, 8, 'https://images.unsplash.com/photo-1586075010923-2dd4570fb338?w=800&q=80', 0, 1);

-- HERO SLIDES
INSERT INTO `hero_slide` (`id`, `heading`, `description`, `tag`, `media_type`, `media_url`, `alt_text`, `display_order`, `is_active`) VALUES
(1, 'Timeless Heritage, Modern Grace', 'Discover handcrafted fine jewellery sculpted with ethical gemstones and pure gold.', 'THE HEIRLOOM COLLECTION', 'IMAGE', 'https://images.unsplash.com/photo-1515562141207-7a88fb7ce338?w=1600&q=85', 'AuraCraft Studio Fine Jewellery Collection', 1, 1),
(2, 'Bespoke Bridal & Solitaires', 'Handcrafted engagement rings and wedding bands designed to celebrate the moments that define a lifetime.', 'BRIDAL EXCLUSIVES', 'IMAGE', 'https://images.unsplash.com/photo-1605100804763-247f67b3557e?w=1600&q=85', 'Solitaire Diamond Engagement Ring', 2, 1),
(3, 'Artisan Keepsakes & Wax Seals', 'Personalized brass wax stamps, monogram signets, and bespoke stationery crafted for heirloom letter-writing.', 'CUSTOM CRAFTSMANSHIP', 'IMAGE', 'https://images.unsplash.com/photo-1579783902614-a3fb3927b675?w=1600&q=85', 'Artisan Custom Wax Seal Stamp', 3, 1);

-- STORE VIDEOS / SOCIAL REELS
INSERT INTO `store_videos` (`id`, `title`, `caption`, `video_category`, `platform`, `video_url`, `thumbnail_url`, `product_id`, `customer_name`, `rating`, `cta_text`, `display_order`, `is_active`) VALUES
(1, 'Master Goldsmith Polishing Solitaire Ring', 'Every angle finished with precision and timeless beauty in our Colombo workshop.', 'CRAFTSMANSHIP', 'DIRECT_URL', 'https://assets.mixkit.co/videos/preview/mixkit-hands-of-a-jeweler-polishing-a-ring-41617-large.mp4', 'https://images.unsplash.com/photo-1605100804763-247f67b3557e?w=600&q=80', 1, 'Master Artisan', 5, 'View Rings', 1, 1),
(2, 'Unboxing The Celeste Pearl Pendant', 'Complimentary velvet box, certification card, and gold wax ribbon with every piece.', 'SHOP_THE_LOOK', 'DIRECT_URL', 'https://assets.mixkit.co/videos/preview/mixkit-putting-a-shiny-ring-into-a-gift-box-41620-large.mp4', 'https://images.unsplash.com/photo-1599643478518-a784e5dc4c8f?w=600&q=80', 3, 'Amara Perera', 5, 'Shop Celeste', 2, 1);

-- CUSTOMER REVIEWS
INSERT INTO `reviews` (`id`, `variant_id`, `user_id`, `rating`, `comment`) VALUES
(1, 1, 2, 5, 'Absolutely breathtaking ring! The diamond brilliance and gold finishing surpassed all my expectations.'),
(2, 6, 2, 5, 'The baroque pearl has such a unique, organic shape. Delivered in lovely packaging within 2 days.'),
(3, 12, 2, 5, 'The custom brass wax seal stamps cleanly with sharp details. Gives our wedding invitations a royal touch!');

SET FOREIGN_KEY_CHECKS = 1;
