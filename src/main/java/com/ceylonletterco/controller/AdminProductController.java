package com.auracraft.controller;

import com.auracraft.entity.*;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import com.auracraft.service.CloudinaryService;

/**
 * AdminProductController – migrated from AdminServlet/AdminProductServlet.
 * Handles /api/admin/products/* endpoints (admin product & category management).
 */
@RestController
@RequestMapping("/api/admin")
@Transactional
public class AdminProductController {

    @PersistenceContext
    private EntityManager em;
    
    @org.springframework.beans.factory.annotation.Autowired
    private com.auracraft.service.EmailVerificationService emailService;
    
    @org.springframework.beans.factory.annotation.Autowired
    private com.auracraft.service.NotificationService notificationService;

    @org.springframework.beans.factory.annotation.Autowired
    private com.auracraft.service.AuditLogService auditLogService;

    @Autowired
    private CloudinaryService cloudinaryService;

    private boolean isAdminOrStaff(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        User u = (session != null) ? (User) session.getAttribute("loggedInUser") : null;
        if (u == null) return false;
        String role = u.getRole() != null ? u.getRole().toUpperCase() : "";
        return !"CUSTOMER".equals(role);
    }

    // ── GET /api/admin/products ──────────────────────────────────────────────
    @GetMapping(value = "/products", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<String> getProducts(HttpServletRequest request) {
        if (!isAdminOrStaff(request)) return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");

        List<Product> products = em.createQuery(
                "SELECT p FROM Product p ORDER BY p.createdAt DESC", Product.class).getResultList();
        StringBuilder arr = new StringBuilder("[");
        for (int i = 0; i < products.size(); i++) {
            if (i > 0) arr.append(",");
            Product p = products.get(i);
            arr.append("{")
                .append("\"id\":").append(p.getId()).append(",")
                .append("\"name\":\"").append(esc(p.getName())).append("\",")
                .append("\"sku\":\"").append(esc(p.getSku())).append("\",")
                .append("\"brand\":\"").append(esc(p.getBrand())).append("\",")
                .append("\"isActive\":").append(Boolean.TRUE.equals(p.getIsActive())).append(",")
                .append("\"isDeleted\":").append(Boolean.TRUE.equals(p.getIsDeleted())).append(",")
                .append("\"basePrice\":").append(p.getBasePrice() != null ? p.getBasePrice() : 0).append(",")
                .append("\"isCustomisable\":").append(Boolean.TRUE.equals(p.getIsCustomisable())).append(",")
                .append("\"requiresDeposit\":").append(Boolean.TRUE.equals(p.getRequiresDeposit())).append(",")
                .append("\"warrantyPeriod\":\"").append(esc(p.getWarrantyPeriod() != null ? p.getWarrantyPeriod() : "")).append("\",")
                .append("\"category\":\"").append(p.getCategory() != null ? esc(p.getCategory().getName()) : "Uncategorized").append("\",")
                .append("\"createdAt\":\"").append(p.getCreatedAt() != null ? p.getCreatedAt().toString() : "").append("\"")
                .append("}");
        }
        arr.append("]");
        return ResponseEntity.ok("{\"success\":true,\"products\":" + arr + "}");
    }

    // ── GET /api/admin/products/dropdown-with-inventory ──────────────────────
    @GetMapping(value = "/products/dropdown-with-inventory", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<String> getProductsDropdown(HttpServletRequest request) {
        if (!isAdminOrStaff(request)) return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");

        List<Product> products = em.createQuery(
                "SELECT p FROM Product p WHERE (p.isDeleted = false OR p.isDeleted IS NULL) ORDER BY p.createdAt DESC", Product.class).getResultList();
        
        List<com.auracraft.entity.Inventory> inventories = em.createQuery("SELECT i FROM Inventory i", com.auracraft.entity.Inventory.class).getResultList();
        java.util.Map<Integer, Integer> stockMap = new java.util.HashMap<>();
        for(com.auracraft.entity.Inventory inv : inventories) {
            stockMap.put(inv.getVariantId(), inv.getQuantityOnHand() != null ? inv.getQuantityOnHand() : 0);
        }

        StringBuilder arr = new StringBuilder("[");
        for (int i = 0; i < products.size(); i++) {
            if (i > 0) arr.append(",");
            Product p = products.get(i);
            arr.append("{")
                .append("\"id\":").append(p.getId()).append(",")
                .append("\"name\":\"").append(esc(p.getName())).append("\",")
                .append("\"isCustomisable\":").append(Boolean.TRUE.equals(p.getIsCustomisable())).append(",")
                .append("\"basePrice\":").append(p.getBasePrice() != null ? p.getBasePrice() : 0).append(",")
                .append("\"variants\":[");
                
            java.util.List<ProductVariant> variants = p.getVariants();
            boolean firstV = true;
            for(ProductVariant v : variants) {
                if(Boolean.TRUE.equals(v.getIsDeleted())) continue;
                if(!firstV) arr.append(",");
                arr.append("{")
                   .append("\"id\":").append(v.getId()).append(",")
                   .append("\"sku\":\"").append(esc(v.getSkuVariant())).append("\",")
                   .append("\"color\":\"").append(esc(v.getMetalColor())).append("\",")
                   .append("\"size\":\"").append(esc(v.getSizeLength())).append("\",")
                   .append("\"price\":").append(v.getPrice() != null ? v.getPrice() : 0).append(",")
                   .append("\"stock\":").append(stockMap.getOrDefault(v.getId(), 0))
                   .append("}");
                firstV = false;
            }
            arr.append("]}");
        }
        arr.append("]");
        return ResponseEntity.ok("{\"success\":true,\"products\":" + arr + "}");
    }


    // ── POST /api/admin/products ─────────────────────────────────────────────
    @PostMapping(value = "/products", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createOrUpdateProduct(MultipartHttpServletRequest request) {
        if (!isAdminOrStaff(request)) return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");

        try {
            String productIdStr = request.getParameter("productId");
            Product p;
            if (productIdStr != null && !productIdStr.isEmpty()) {
                p = em.find(Product.class, Integer.parseInt(productIdStr));
                if (p == null) return ResponseEntity.status(404).body("{\"success\":false,\"message\":\"Product not found.\"}");
            } else {
                p = new Product();
            }

            String name = request.getParameter("name");
            if (name == null || name.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"Name is required.\"}");
            }

            p.setName(name.trim());
            p.setBrand(request.getParameter("brand") != null ? request.getParameter("brand").trim() : "");
            p.setSummary(request.getParameter("summary") != null ? request.getParameter("summary").trim() : "");
            p.setDescription(request.getParameter("description") != null ? request.getParameter("description").trim() : "");
            p.setGender(request.getParameter("gender") != null ? request.getParameter("gender").trim() : "UNISEX");
            p.setAvailabilityStatus(request.getParameter("availabilityStatus") != null ? request.getParameter("availabilityStatus").trim() : "IN_STOCK");
            p.setIsCustomisable("true".equals(request.getParameter("isCustomisable")));
            p.setWarrantyPeriod(request.getParameter("warrantyPeriod") != null ? request.getParameter("warrantyPeriod").trim() : "");
            p.setRequiresDeposit("true".equals(request.getParameter("requiresDeposit")));
            p.setIsActive(true); // default active

            String categoryIdStr = request.getParameter("categoryId");
            if (categoryIdStr != null && !categoryIdStr.isEmpty() && !categoryIdStr.equals("0")) {
                Category cat = em.find(Category.class, Integer.parseInt(categoryIdStr));
                p.setCategory(cat);
            }

            boolean isNewProduct = (p.getId() == null);
            if (isNewProduct) {
                // To get primary SKU from variant 0 for the product itself
                String primarySku = request.getParameter("skuVariant");
                if (primarySku != null) p.setSku(primarySku.trim());
                em.persist(p);
            } else {
                em.merge(p);
            }
            em.flush();

            // Handle Variants
            // We iterate up to 100 variants to be safe
            for (int i = 0; i < 100; i++) {
                String skuParam = i == 0 ? "skuVariant" : "variant_sku_" + i;
                String skuVal = request.getParameter(skuParam);
                
                if (skuVal != null && !skuVal.trim().isEmpty()) {
                    String idParam = i == 0 ? "variant_id_0" : "variant_id_" + i;
                    String idVal = request.getParameter(idParam);
                    
                    String colorParam = i == 0 ? "color" : "variant_color_" + i;
                    String colorVal = request.getParameter(colorParam);
                    
                    String sizeParam = i == 0 ? "sizeCapacity" : "variant_size_" + i;
                    String sizeVal = request.getParameter(sizeParam);
                    
                    String priceParam = i == 0 ? "price" : "variant_price_" + i;
                    String priceVal = request.getParameter(priceParam);
                    
                    String compareParam = i == 0 ? "compareAtPrice" : "variant_compare_" + i;
                    String compareVal = request.getParameter(compareParam);
                    
                    String costParam = i == 0 ? "costPrice" : "variant_cost_" + i;
                    String costVal = request.getParameter(costParam);
                    
                    ProductVariant v;
                    boolean isNewVariant = false;
                    if (idVal != null && !idVal.trim().isEmpty()) {
                        v = em.find(ProductVariant.class, Integer.parseInt(idVal));
                        if (v == null) {
                            v = new ProductVariant();
                            isNewVariant = true;
                        }
                    } else {
                        v = new ProductVariant();
                        isNewVariant = true;
                    }
                    
                    v.setProduct(p);
                    v.setSkuVariant(skuVal.trim());
                    if (colorVal != null) v.setMetalColor(colorVal.trim());
                    if (sizeVal != null) v.setSizeLength(sizeVal.trim());
                    if (priceVal != null && !priceVal.isEmpty()) {
                        try { v.setPrice(new BigDecimal(priceVal)); } catch (Exception ignored) {}
                    }
                    if (compareVal != null && !compareVal.isEmpty()) {
                        try { v.setCompareAtPrice(new BigDecimal(compareVal)); } catch (Exception ignored) {}
                    } else {
                        v.setCompareAtPrice(null);
                    }
                    if (costVal != null && !costVal.isEmpty()) {
                        try { v.setCostPrice(new BigDecimal(costVal)); } catch (Exception ignored) {}
                    } else {
                        v.setCostPrice(BigDecimal.ZERO);
                    }
                    v.setIsDeleted(false);
                    
                    if (isNewVariant) {
                        em.persist(v);
                        em.flush();
                        
                        // Auto-create Inventory record
                        Inventory inv = new Inventory();
                        inv.setProductVariant(v);
                        inv.setQuantityOnHand(0);
                        inv.setLowStockThreshold(5);
                        em.persist(inv);
                    } else {
                        em.merge(v);
                    }
                }
            }
            em.flush();
            
            // Handle Images
            String primaryImgIdx = request.getParameter("primaryImageIndex");
            int primaryIdx = 1;
            if (primaryImgIdx != null && !primaryImgIdx.isEmpty()) {
                try { primaryIdx = Integer.parseInt(primaryImgIdx); } catch (Exception ignored) {}
            }
            
            for (int i = 1; i <= 4; i++) {
                MultipartFile file = request.getFile("image_" + i);
                if (file != null && !file.isEmpty()) {
                    String imgUrl = cloudinaryService.uploadImage(file);
                    
                    // See if an image for this slot already exists (using sortOrder)
                    List<ProductImage> existingImages = em.createQuery("SELECT pi FROM ProductImage pi WHERE pi.product.id = :pid AND pi.sortOrder = :order", ProductImage.class)
                            .setParameter("pid", p.getId())
                            .setParameter("order", i)
                            .getResultList();
                            
                    ProductImage pi;
                    if (!existingImages.isEmpty()) {
                        pi = existingImages.get(0);
                    } else {
                        pi = new ProductImage();
                        pi.setProduct(p);
                        pi.setSortOrder(i);
                    }
                    
                    pi.setImageUrl(imgUrl);
                    pi.setIsPrimary(i == primaryIdx);
                    
                    if (pi.getId() == null) em.persist(pi);
                    else em.merge(pi);
                } else {
                    // Update primary status of existing images if no new file is uploaded for this slot
                    List<ProductImage> existingImages = em.createQuery("SELECT pi FROM ProductImage pi WHERE pi.product.id = :pid AND pi.sortOrder = :order", ProductImage.class)
                            .setParameter("pid", p.getId())
                            .setParameter("order", i)
                            .getResultList();
                    if (!existingImages.isEmpty()) {
                        ProductImage pi = existingImages.get(0);
                        pi.setIsPrimary(i == primaryIdx);
                        em.merge(pi);
                    }
                }
            }

            String primaryImageUrl = "";
            List<ProductImage> primaryImages = em.createQuery("SELECT pi FROM ProductImage pi WHERE pi.product.id = :pid AND pi.isPrimary = true", ProductImage.class)
                    .setParameter("pid", p.getId())
                    .getResultList();
            if (!primaryImages.isEmpty()) {
                primaryImageUrl = primaryImages.get(0).getImageUrl();
            }

            if (isNewProduct) {
                try {
                    emailService.sendNewProductBroadcast(p, primaryImageUrl);
                    notificationService.broadcastToCustomers("NEW_PRODUCT", "New Arrival: " + p.getName(), "/product-view.html?id=" + p.getId());
                    notificationService.notifyStaffByRole(java.util.List.of("ADMIN", "MANAGER", "STOCK_MANAGER"), "NEW_PRODUCT", "New product added: " + p.getName(), "/admin.html");
                } catch (Exception ignored) {}
            }

            auditLogService.log(request, isNewProduct ? "CREATE_PRODUCT" : "UPDATE_PRODUCT", "PRODUCT",
                    (isNewProduct ? "Created product: " : "Updated product: ") + p.getName() + " [ID: " + p.getId() + "]",
                    "SUCCESS");

            return ResponseEntity.ok("{\"success\":true,\"message\":\"Product saved successfully.\",\"id\":" + p.getId() + "}");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── PUT /api/admin/products/{id} ─────────────────────────────────────────
    @PutMapping(value = "/products/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> updateProduct(@PathVariable int id, @RequestBody JsonNode body,
                                                 HttpServletRequest request) {
        if (!isAdminOrStaff(request)) return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");

        try {
            Product p = em.find(Product.class, id);
            if (p == null) return ResponseEntity.status(404).body("{\"success\":false,\"message\":\"Product not found.\"}");
            if (!body.path("name").isMissingNode()) p.setName(body.path("name").asText().trim());
            if (!body.path("brand").isMissingNode()) p.setBrand(body.path("brand").asText().trim());
            if (!body.path("summary").isMissingNode()) p.setSummary(body.path("summary").asText().trim());
            if (!body.path("description").isMissingNode()) p.setDescription(body.path("description").asText().trim());
            if (!body.path("isActive").isMissingNode()) p.setIsActive(body.path("isActive").asBoolean(true));
            if (!body.path("isDeleted").isMissingNode()) p.setIsDeleted(body.path("isDeleted").asBoolean(false));
            if (!body.path("requiresDeposit").isMissingNode()) p.setRequiresDeposit(body.path("requiresDeposit").asBoolean(false));
            if (!body.path("isCustomisable").isMissingNode()) p.setIsCustomisable(body.path("isCustomisable").asBoolean(false));
            if (!body.path("availabilityStatus").isMissingNode()) p.setAvailabilityStatus(body.path("availabilityStatus").asText());
            if (!body.path("categoryId").isMissingNode() && body.path("categoryId").asInt(0) > 0) {
                p.setCategory(em.find(Category.class, body.path("categoryId").asInt()));
            }
            em.merge(p);

            auditLogService.log(request, "UPDATE_PRODUCT", "PRODUCT",
                    "Updated product details: " + p.getName() + " [ID: " + p.getId() + "]",
                    "SUCCESS");

            return ResponseEntity.ok("{\"success\":true,\"message\":\"Product updated.\"}");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── DELETE /api/admin/products/{id} ──────────────────────────────────────
    @DeleteMapping(value = "/products/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> deleteProduct(@PathVariable int id, HttpServletRequest request) {
        if (!isAdminOrStaff(request)) return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");

        try {
            Product p = em.find(Product.class, id);
            if (p == null) return ResponseEntity.status(404).body("{\"success\":false,\"message\":\"Product not found.\"}");
            p.setIsDeleted(true);
            em.merge(p);

            auditLogService.log(request, "DELETE_PRODUCT", "PRODUCT",
                    "Deleted product: " + p.getName() + " [ID: " + p.getId() + "]",
                    "SUCCESS");

            return ResponseEntity.ok("{\"success\":true,\"message\":\"Product soft-deleted.\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── GET /api/admin/categories ─────────────────────────────────────────────
    @GetMapping(value = "/categories", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<String> getCategories(HttpServletRequest request) {
        if (!isAdminOrStaff(request)) return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");

        List<Category> cats = em.createQuery("SELECT c FROM Category c ORDER BY c.name ASC", Category.class).getResultList();
        StringBuilder arr = new StringBuilder("[");
        for (int i = 0; i < cats.size(); i++) {
            if (i > 0) arr.append(",");
            Category c = cats.get(i);
            arr.append("{\"id\":").append(c.getId())
                .append(",\"name\":\"").append(esc(c.getName())).append("\"")
                .append(",\"imageUrl\":\"").append(esc(c.getImageUrl())).append("\"")
                .append(",\"isActive\":").append(Boolean.TRUE.equals(c.getIsActive()))
                .append("}");
        }
        arr.append("]");
        return ResponseEntity.ok("{\"success\":true,\"categories\":" + arr + "}");
    }

    // ── POST /api/admin/categories ────────────────────────────────────────────
    @PostMapping(value = "/categories", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createCategory(@RequestBody JsonNode body, HttpServletRequest request) {
        if (!isAdminOrStaff(request)) return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");
        try {
            String name = body.path("name").asText("").trim();
            if (name.isEmpty()) return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"Category name is required.\"}");
            Category c = new Category();
            c.setName(name);
            c.setDescription(body.path("description").asText("").trim());
            c.setImageUrl(body.path("imageUrl").asText("").trim());
            c.setIsActive(body.path("isActive").asBoolean(true));
            em.persist(c);
            em.flush();
            return ResponseEntity.ok("{\"success\":true,\"message\":\"Category created.\",\"id\":" + c.getId() + "}");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── POST /api/admin/products/bulk-import ──────────────────────────────────
    @PostMapping(value = "/products/bulk-import", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> bulkImportProducts(@RequestBody JsonNode body, HttpServletRequest request) {
        if (!isAdminOrStaff(request))
            return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");

        try {
            JsonNode items = body.path("items");
            if (!items.isArray() || items.isEmpty()) {
                return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"No items provided for import.\"}");
            }

            int productsCreated = 0;
            int variantsCreated = 0;
            Map<String, Product> productMap = new java.util.HashMap<>();
            Map<String, Category> categoryMap = new java.util.HashMap<>();

            List<Category> existingCats = em.createQuery("SELECT c FROM Category c", Category.class).getResultList();
            for (Category c : existingCats) {
                categoryMap.put(c.getName().trim().toLowerCase(), c);
            }

            for (JsonNode item : items) {
                String name = item.path("name").asText("").trim();
                if (name.isEmpty()) continue;

                String catName = item.path("category").asText("Default").trim();
                String description = item.path("description").asText("");
                String sku = item.path("sku").asText("").trim();
                String color = item.path("color").asText("").trim();
                String size = item.path("size").asText("").trim();

                BigDecimal price = BigDecimal.ZERO;
                try { price = new BigDecimal(item.path("price").asText("0").trim()); } catch (Exception ignored) {}

                BigDecimal compareAtPrice = null;
                if (item.has("compareAtPrice") && !item.path("compareAtPrice").asText().isBlank()) {
                    try { compareAtPrice = new BigDecimal(item.path("compareAtPrice").asText().trim()); } catch (Exception ignored) {}
                }

                BigDecimal costPrice = BigDecimal.ZERO;
                if (item.has("costPrice") && !item.path("costPrice").asText().isBlank()) {
                    try { costPrice = new BigDecimal(item.path("costPrice").asText().trim()); } catch (Exception ignored) {}
                }

                int stock = item.path("stock").asInt(10);
                String imageUrl = item.path("imageUrl").asText("").trim();

                String productKey = (name + "||" + catName).toLowerCase();
                Product product = productMap.get(productKey);
                if (product == null) {
                    // Check if already in DB
                    List<Product> existing = em.createQuery("SELECT p FROM Product p WHERE LOWER(p.name) = :n", Product.class)
                            .setParameter("n", name.toLowerCase())
                            .getResultList();
                    if (!existing.isEmpty()) {
                        product = existing.get(0);
                    } else {
                        product = new Product();
                        product.setName(name);
                        product.setDescription(description);
                        product.setSku(sku.isEmpty() ? "PROD-" + System.currentTimeMillis() : sku);
                        product.setBasePrice(price);
                        product.setIsActive(true);

                        Category cat = categoryMap.get(catName.toLowerCase());
                        if (cat == null) {
                            cat = new Category();
                            cat.setName(catName);
                            em.persist(cat);
                            em.flush();
                            categoryMap.put(catName.toLowerCase(), cat);
                        }
                        product.setCategory(cat);
                        em.persist(product);
                        em.flush();
                        productsCreated++;
                    }
                    productMap.put(productKey, product);

                    if (!imageUrl.isEmpty()) {
                        ProductImage img = new ProductImage();
                        img.setProduct(product);
                        img.setImageUrl(imageUrl);
                        img.setIsPrimary(true);
                        img.setSortOrder(0);
                        em.persist(img);
                    }
                }

                ProductVariant variant = new ProductVariant();
                variant.setProduct(product);
                if (sku.isEmpty()) {
                    sku = "SKU-" + name.replaceAll("[^a-zA-Z0-9]", "").toUpperCase() + "-" + (variantsCreated + 1);
                }
                variant.setSkuVariant(sku);
                variant.setMetalColor(color.isEmpty() ? "Standard" : color);
                variant.setSizeLength(size.isEmpty() ? "Standard" : size);
                variant.setPrice(price);
                variant.setCompareAtPrice(compareAtPrice);
                variant.setCostPrice(costPrice);
                variant.setIsDeleted(false);
                em.persist(variant);
                em.flush();

                Inventory inv = new Inventory();
                inv.setProductVariant(variant);
                inv.setQuantityOnHand(Math.max(0, stock));
                inv.setLowStockThreshold(5);
                em.persist(inv);

                variantsCreated++;
            }

            return ResponseEntity.ok(String.format(
                "{\"success\":true,\"message\":\"Imported %d products with %d variants successfully!\",\"productsCreated\":%d,\"variantsCreated\":%d}",
                productsCreated, variantsCreated, productsCreated, variantsCreated
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── GET /api/admin/orders ─────────────────────────────────────────────────
    @GetMapping(value = "/orders", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<String> getAllOrders(HttpServletRequest request) {
        if (!isAdminOrStaff(request)) return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");

        List<Order> orders = em.createQuery("SELECT o FROM Order o", Order.class).getResultList();
        List<PosOrder> posOrders = em.createQuery("SELECT po FROM PosOrder po", PosOrder.class).getResultList();
        
        // Merge and sort
        List<Object> allOrders = new java.util.ArrayList<>();
        allOrders.addAll(orders);
        allOrders.addAll(posOrders);
        allOrders.sort((a, b) -> {
            java.time.LocalDateTime dtA = (a instanceof Order) ? ((Order) a).getCreatedAt() : ((PosOrder) a).getCreatedAt();
            java.time.LocalDateTime dtB = (b instanceof Order) ? ((Order) b).getCreatedAt() : ((PosOrder) b).getCreatedAt();
            if (dtA == null && dtB == null) return 0;
            if (dtA == null) return 1;
            if (dtB == null) return -1;
            return dtB.compareTo(dtA);
        });

        StringBuilder arr = new StringBuilder("[");
        for (int i = 0; i < allOrders.size(); i++) {
            if (i > 0) arr.append(",");
            Object obj = allOrders.get(i);
            
            if (obj instanceof Order) {
                Order o = (Order) obj;
                arr.append("{")
                    .append("\"isPos\":false,")
                    .append("\"id\":").append(o.getId()).append(",")
                    .append("\"userId\":").append(o.getUser().getId()).append(",")
                    .append("\"customerName\":\"").append(esc(o.getUser().getFullName())).append("\",")
                    .append("\"customerEmail\":\"").append(esc(o.getUser().getEmail())).append("\",")
                    .append("\"customerPhone\":\"").append(esc(o.getUser().getPhone())).append("\",")
                    .append("\"orderStatus\":\"").append(esc(o.getOrderStatus())).append("\",")
                    .append("\"paymentStatus\":\"").append(esc(o.getPaymentStatus())).append("\",")
                    .append("\"totalAmount\":").append(o.getTotalAmount()).append(",")
                    .append("\"createdAt\":\"").append(o.getCreatedAt() != null ? o.getCreatedAt().toString() : "").append("\",");

                List<com.auracraft.entity.Payment> payments = em.createQuery("SELECT p FROM Payment p WHERE p.order = :ord", com.auracraft.entity.Payment.class)
                                                                     .setParameter("ord", o).getResultList();
                String paymentMethod = payments.isEmpty() ? "N/A" : payments.get(0).getPaymentMethod();
                String slipUrl = payments.isEmpty() || payments.get(0).getSlipImageUrl() == null ? "" : payments.get(0).getSlipImageUrl();
                String rawTx = (!payments.isEmpty() && payments.get(0).getTransactionId() != null) ? payments.get(0).getTransactionId() : "";
                String txId = (rawTx.isEmpty() || rawTx.startsWith("http"))
                    ? switch (paymentMethod) {
                        case "PAYHERE", "CARD", "STRIPE" -> "TXN-CARD-";
                        case "BANK_TRANSFER" -> "TXN-BNK-";
                        case "COD" -> "TXN-COD-";
                        case "COD_WITH_DEPOSIT", "COD_WITH_BANK_DEPOSIT", "ADVANCE_COD" -> "TXN-ADV-";
                        default -> "TXN-";
                      } + String.format("%05d", o.getId())
                    : rawTx;

                BigDecimal total = o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO;
                BigDecimal adv = BigDecimal.ZERO;
                BigDecimal cod = total;

                if ("PAID".equals(o.getPaymentStatus()) || "DELIVERED".equals(o.getOrderStatus())) {
                    adv = total;
                    cod = BigDecimal.ZERO;
                } else if (paymentMethod.contains("DEPOSIT") || paymentMethod.contains("ADVANCE")) {
                    adv = new BigDecimal("1000.00");
                    if (adv.compareTo(total) > 0) adv = total;
                    cod = total.subtract(adv);
                } else if (!payments.isEmpty() && payments.get(0).getAmount() != null) {
                    if ("PAID".equals(payments.get(0).getPaymentStatus()) || "DEPOSIT_PAID".equals(payments.get(0).getPaymentStatus())) {
                        adv = payments.get(0).getAmount();
                        if (adv.compareTo(total) > 0) adv = total;
                        cod = total.subtract(adv);
                    }
                }

                arr.append("\"paymentMethod\":\"").append(esc(paymentMethod)).append("\",")
                   .append("\"transactionId\":\"").append(esc(txId)).append("\",")
                   .append("\"slipImageUrl\":\"").append(esc(slipUrl)).append("\",")
                   .append("\"advancePaid\":").append(adv.toPlainString()).append(",")
                   .append("\"codBalance\":").append(cod.toPlainString()).append(",")
                   .append("\"items\":[");
                List<OrderItem> items = em.createQuery("SELECT i FROM OrderItem i JOIN FETCH i.productVariant v JOIN FETCH v.product p WHERE i.order = :ord", OrderItem.class)
                                          .setParameter("ord", o).getResultList();
                for (int j = 0; j < items.size(); j++) {
                    if (j > 0) arr.append(",");
                    OrderItem item = items.get(j);
                    ProductVariant v = item.getProductVariant();
                    Product p = v.getProduct();
                    arr.append("{")
                       .append("\"quantity\":").append(item.getQuantity()).append(",")
                       .append("\"productName\":\"").append(esc(p.getName())).append("\",")
                       .append("\"color\":\"").append(esc(v.getMetalColor() != null ? v.getMetalColor() : "")).append("\",")
                       .append("\"size\":\"").append(esc(v.getSizeLength() != null ? v.getSizeLength() : "")).append("\",")
                       .append("\"engravingText\":\"").append(esc(item.getEngravingText() != null ? item.getEngravingText() : "")).append("\",")
                       .append("\"customResize\":\"").append(esc(item.getCustomResize() != null ? item.getCustomResize() : "")).append("\"")
                       .append("}");
                }
                arr.append("]}");
            } else {
                PosOrder po = (PosOrder) obj;
                String posMethod = po.getPaymentMethod() != null ? po.getPaymentMethod() : "";
                String posTxId = switch (posMethod) {
                    case "BANK_TRANSFER" -> "TXN-BNK-POS-";
                    case "COD" -> "TXN-COD-POS-";
                    case "FULL_PAID" -> "TXN-POS-";
                    case "WARRANTY_CLAIM" -> "TXN-WRN-POS-";
                    case "COD_WITH_DEPOSIT", "COD_WITH_BANK_DEPOSIT", "ADVANCE_COD" -> "TXN-ADV-POS-";
                    default -> "TXN-POS-";
                } + String.format("%05d", po.getId());

                BigDecimal posAdv = po.getAdvancePaid() != null ? po.getAdvancePaid() : BigDecimal.ZERO;
                BigDecimal posCod = po.getCodBalance() != null ? po.getCodBalance() : BigDecimal.ZERO;
                String posPayStatus = "PENDING";
                if ("WARRANTY_CLAIM".equals(posMethod) || Boolean.TRUE.equals(po.getIsWarrantyReplacement())) {
                    posPayStatus = "WARRANTY_CLAIM";
                    posAdv = BigDecimal.ZERO;
                    posCod = BigDecimal.ZERO;
                } else if ("DELIVERED".equals(po.getOrderStatus()) || "FULL_PAID".equals(posMethod)) {
                    posPayStatus = "PAID";
                    posAdv = po.getTotalAmount();
                    posCod = BigDecimal.ZERO;
                }

                arr.append("{")
                    .append("\"isPos\":true,")
                    .append("\"id\":").append(po.getId()).append(",")
                    .append("\"userId\":0,")
                    .append("\"customerName\":\"").append(esc(po.getCustomerName())).append("\",")
                    .append("\"customerEmail\":\"").append(esc(po.getSalesRep().getEmail())).append("\",")
                    .append("\"salesRepName\":\"").append(esc(po.getSalesRep().getFullName())).append("\",")
                    .append("\"customerPhone\":\"").append(esc(po.getPhone1())).append("\",")
                    .append("\"orderStatus\":\"").append(esc(po.getOrderStatus())).append("\",")
                    .append("\"paymentStatus\":\"").append(esc(posPayStatus)).append("\",")
                    .append("\"subtotal\":").append(po.getSubtotal() != null ? po.getSubtotal() : 0).append(",")
                    .append("\"discountAmount\":").append(po.getDiscountAmount() != null ? po.getDiscountAmount() : 0).append(",")
                    .append("\"deliveryCharge\":").append(po.getDeliveryCharge()).append(",")
                    .append("\"totalAmount\":").append(po.getTotalAmount()).append(",")
                    .append("\"advancePaid\":").append(posAdv.toPlainString()).append(",")
                    .append("\"codBalance\":").append(posCod.toPlainString()).append(",")
                    .append("\"createdAt\":\"").append(po.getCreatedAt() != null ? po.getCreatedAt().toString() : "").append("\",")
                    .append("\"paymentMethod\":\"").append(esc(posMethod)).append("\",")
                    .append("\"transactionId\":\"").append(esc(posTxId)).append("\",")
                    .append("\"slipImageUrl\":\"").append(esc(po.getPaymentSlipUrl() != null ? po.getPaymentSlipUrl() : "")).append("\",")
                    .append("\"items\":[");
                List<PosOrderItem> items = em.createQuery("SELECT i FROM PosOrderItem i JOIN FETCH i.productVariant v JOIN FETCH v.product p WHERE i.posOrder = :ord", PosOrderItem.class)
                                          .setParameter("ord", po).getResultList();
                for (int j = 0; j < items.size(); j++) {
                    if (j > 0) arr.append(",");
                    PosOrderItem item = items.get(j);
                    ProductVariant v = item.getProductVariant();
                    Product p = v.getProduct();
                    arr.append("{")
                       .append("\"quantity\":").append(item.getQuantity()).append(",")
                       .append("\"productName\":\"").append(esc(p.getName())).append("\",")
                       .append("\"color\":\"").append(esc(v.getMetalColor() != null ? v.getMetalColor() : "")).append("\",")
                       .append("\"size\":\"").append(esc(v.getSizeLength() != null ? v.getSizeLength() : "")).append("\",")
                       .append("\"engravingText\":\"").append(esc(item.getEngravingText() != null ? item.getEngravingText() : "")).append("\",")
                       .append("\"customResize\":\"").append(esc(item.getCustomResize() != null ? item.getCustomResize() : "")).append("\"")
                       .append("}");
                }
                arr.append("]}");
            }
        }
        arr.append("]");
        return ResponseEntity.ok("{\"success\":true,\"orders\":" + arr + "}");
    }

    // ── GET /api/admin/orders/{id} ────────────────────────────────────────────
    @GetMapping(value = "/orders/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<String> getOrder(@PathVariable int id, HttpServletRequest request) {
        if (!isAdminOrStaff(request)) return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");
        Order o = em.find(Order.class, id);
        if (o == null) return ResponseEntity.status(404).body("{\"success\":false,\"message\":\"Order not found.\"}");

        StringBuilder arr = new StringBuilder();
        arr.append("{")
            .append("\"id\":").append(o.getId()).append(",")
            .append("\"userId\":").append(o.getUser().getId()).append(",")
            .append("\"customerName\":\"").append(esc(o.getUser().getFullName())).append("\",")
            .append("\"customerEmail\":\"").append(esc(o.getUser().getEmail())).append("\",")
            .append("\"customerPhone\":\"").append(esc(o.getUser().getPhone())).append("\",")
            .append("\"orderStatus\":\"").append(esc(o.getOrderStatus())).append("\",")
            .append("\"paymentStatus\":\"").append(esc(o.getPaymentStatus())).append("\",")
            .append("\"totalAmount\":").append(o.getTotalAmount()).append(",")
            .append("\"createdAt\":\"").append(o.getCreatedAt() != null ? o.getCreatedAt().toString() : "").append("\",");

        List<com.auracraft.entity.Payment> payments = em.createQuery("SELECT p FROM Payment p WHERE p.order = :ord", com.auracraft.entity.Payment.class)
                                                             .setParameter("ord", o).getResultList();
        String paymentMethod = payments.isEmpty() ? "N/A" : payments.get(0).getPaymentMethod();
        String slipUrl = payments.isEmpty() || payments.get(0).getSlipImageUrl() == null ? "" : payments.get(0).getSlipImageUrl();
        arr.append("\"paymentMethod\":\"").append(esc(paymentMethod)).append("\",")
           .append("\"slipImageUrl\":\"").append(esc(slipUrl)).append("\",")
           .append("\"items\":[");
        List<OrderItem> items = em.createQuery("SELECT i FROM OrderItem i JOIN FETCH i.productVariant v JOIN FETCH v.product p WHERE i.order = :ord", OrderItem.class)
                                  .setParameter("ord", o).getResultList();
        for (int j = 0; j < items.size(); j++) {
            if (j > 0) arr.append(",");
            OrderItem item = items.get(j);
            ProductVariant v = item.getProductVariant();
            Product p = v.getProduct();
            arr.append("{")
               .append("\"quantity\":").append(item.getQuantity()).append(",")
               .append("\"productName\":\"").append(esc(p.getName())).append("\",")
               .append("\"color\":\"").append(esc(v.getMetalColor() != null ? v.getMetalColor() : "")).append("\",")
               .append("\"size\":\"").append(esc(v.getSizeLength() != null ? v.getSizeLength() : "")).append("\",")
               .append("\"engravingText\":\"").append(esc(item.getEngravingText() != null ? item.getEngravingText() : "")).append("\",")
               .append("\"customResize\":\"").append(esc(item.getCustomResize() != null ? item.getCustomResize() : "")).append("\"")
               .append("}");
        }
        arr.append("]")
           .append("}");

        return ResponseEntity.ok("{\"success\":true,\"order\":" + arr + "}");
    }

    // ── POST /api/admin/orders/bulk-status ────────────────────────────────────
    @PostMapping(value = "/orders/bulk-status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> bulkUpdateOrderStatus(@RequestBody JsonNode body, HttpServletRequest request) {
        if (!isAdminOrStaff(request)) return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");
        try {
            JsonNode idsNode = body.path("orderIds");
            String newStatus = body.path("orderStatus").asText("").toUpperCase();
            if (!idsNode.isArray() || idsNode.isEmpty() || newStatus.isEmpty()) {
                return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"Order IDs and status are required.\"}");
            }
            int updatedCount = 0;
            int skippedCount = 0;
            for (JsonNode idNode : idsNode) {
                int id = idNode.asInt();
                Order order = em.find(Order.class, id);
                if (order != null) {
                    String cur = order.getOrderStatus() != null ? order.getOrderStatus().toUpperCase() : "PENDING";
                    // Reverse protection: DELIVERED, CANCELLED, RETURNED cannot revert to PENDING/PROCESSING
                    if (("DELIVERED".equals(cur) || "CANCELLED".equals(cur) || "RETURNED".equals(cur)) &&
                        ("PENDING".equals(newStatus) || "PROCESSING".equals(newStatus))) {
                        skippedCount++;
                        continue;
                    }
                    order.setOrderStatus(newStatus);
                    if ("DELIVERED".equals(newStatus)) {
                        order.setPaymentStatus("PAID");
                    }
                    em.merge(order);
                    updatedCount++;
                } else {
                    PosOrder posOrder = em.find(PosOrder.class, id);
                    if (posOrder != null) {
                        String cur = posOrder.getOrderStatus() != null ? posOrder.getOrderStatus().toUpperCase() : "PENDING";
                        if (("DELIVERED".equals(cur) || "CANCELLED".equals(cur) || "RETURNED".equals(cur)) &&
                            ("PENDING".equals(newStatus) || "PROCESSING".equals(newStatus))) {
                            skippedCount++;
                            continue;
                        }
                        posOrder.setOrderStatus(newStatus);
                        if ("DELIVERED".equals(newStatus) || "COMPLETED".equals(newStatus)) {
                            posOrder.setCodBalance(java.math.BigDecimal.ZERO);
                        }
                        em.merge(posOrder);
                        updatedCount++;
                    }
                }
            }
            em.flush();
            String msg = String.format("Updated %d order(s) to %s successfully.", updatedCount, newStatus);
            if (skippedCount > 0) {
                msg += String.format(" (%d terminal order(s) skipped to prevent illegal status reversion)", skippedCount);
            }
            return ResponseEntity.ok(String.format("{\"success\":true,\"message\":\"%s\",\"updatedCount\":%d}", esc(msg), updatedCount));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── POST /api/admin/orders/bulk-payment-settle ───────────────────────────
    @PostMapping(value = "/orders/bulk-payment-settle", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> bulkSettlePaymentStatus(@RequestBody JsonNode body, HttpServletRequest request) {
        if (!isAdminOrStaff(request)) return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");
        try {
            JsonNode itemsNode = body.path("orders");
            String targetPaymentStatus = body.path("paymentStatus").asText("PAID").toUpperCase();

            if ((itemsNode.isMissingNode() || !itemsNode.isArray() || itemsNode.isEmpty()) && body.path("orderIds").isArray()) {
                itemsNode = body.path("orderIds");
            }
            if (itemsNode.isMissingNode() || !itemsNode.isArray() || itemsNode.isEmpty()) {
                return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"Orders list is required.\"}");
            }

            int updatedCount = 0;
            java.math.BigDecimal totalAmountSettled = java.math.BigDecimal.ZERO;

            for (JsonNode item : itemsNode) {
                int id;
                boolean isPos = false;
                if (item.isObject()) {
                    id = item.path("id").asInt();
                    isPos = item.path("isPos").asBoolean(false);
                } else {
                    id = item.asInt();
                }

                if (isPos) {
                    PosOrder posOrder = em.find(PosOrder.class, id);
                    if (posOrder != null) {
                        posOrder.setPaymentMethod("FULL_PAID");
                        posOrder.setCodBalance(java.math.BigDecimal.ZERO);
                        posOrder.setAdvancePaid(posOrder.getTotalAmount());
                        em.merge(posOrder);
                        totalAmountSettled = totalAmountSettled.add(posOrder.getTotalAmount() != null ? posOrder.getTotalAmount() : java.math.BigDecimal.ZERO);
                        updatedCount++;
                        auditLogService.log(request, "COD_PAYMENT_COLLECTED", "POS",
                                "Bulk COD Settle: POS Order #POS-" + String.format("%05d", id) + " marked as FULL PAID | Amount: LKR " + posOrder.getTotalAmount(),
                                "SUCCESS");
                    }
                } else {
                    Order order = em.find(Order.class, id);
                    if (order != null) {
                        String oldPayment = order.getPaymentStatus();
                        order.setPaymentStatus(targetPaymentStatus);
                        em.merge(order);
                        totalAmountSettled = totalAmountSettled.add(order.getTotalAmount() != null ? order.getTotalAmount() : java.math.BigDecimal.ZERO);
                        updatedCount++;
                        auditLogService.log(request, "COD_PAYMENT_COLLECTED", "ORDER",
                                "Bulk COD Settle: Web Order #CLC-" + String.format("%05d", id) + " payment status updated from " + oldPayment + " to " + targetPaymentStatus + " | Total: LKR " + order.getTotalAmount() + " (COD Settled)",
                                "SUCCESS");
                    }
                }
            }
            em.flush();
            String msg = String.format("Successfully settled COD payments for %d orders (Total: LKR %,.2f).", updatedCount, totalAmountSettled.doubleValue());
            return ResponseEntity.ok(String.format("{\"success\":true,\"message\":\"%s\",\"updatedCount\":%d,\"totalSettled\":%.2f}", esc(msg), updatedCount, totalAmountSettled.doubleValue()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ── PUT /api/admin/orders/{id}/status ─────────────────────────────────────
    @PutMapping(value = "/orders/{id}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> updateOrderStatus(@PathVariable int id, @RequestBody JsonNode body,
                                                     HttpServletRequest request) {
        if (!isAdminOrStaff(request)) return ResponseEntity.status(403).body("{\"success\":false,\"message\":\"Access denied.\"}");
        try {
            boolean isPos = body.path("isPos").asBoolean(false);
            Order order = em.find(Order.class, id);
            PosOrder posOrder = null;
            if (order == null || isPos) {
                posOrder = em.find(PosOrder.class, id);
            }

            if (posOrder != null) {
                String oldOrderStatus = posOrder.getOrderStatus();
                if (!body.path("orderStatus").isMissingNode()) {
                    String newStatus = body.path("orderStatus").asText().toUpperCase();
                    String curUpper = oldOrderStatus != null ? oldOrderStatus.toUpperCase() : "PENDING";
                    if (("DELIVERED".equals(curUpper) || "CANCELLED".equals(curUpper) || "RETURNED".equals(curUpper)) &&
                        ("PENDING".equals(newStatus) || "PROCESSING".equals(newStatus))) {
                        return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"Completed or cancelled orders cannot be reverted back to PENDING/PROCESSING.\"}");
                    }
                    if (!newStatus.equals(oldOrderStatus)) {
                        posOrder.setOrderStatus(newStatus);
                        if ("DELIVERED".equals(newStatus)) {
                            posOrder.setCodBalance(java.math.BigDecimal.ZERO);
                        }
                    }
                }
                boolean posPaymentChanged = false;
                if (!body.path("paymentStatus").isMissingNode()) {
                    String newStatus = body.path("paymentStatus").asText().toUpperCase();
                    if ("PAID".equals(newStatus)) {
                        posOrder.setPaymentMethod("FULL_PAID");
                        posOrder.setCodBalance(java.math.BigDecimal.ZERO);
                        posOrder.setAdvancePaid(posOrder.getTotalAmount());
                        posPaymentChanged = true;
                    }
                }
                em.merge(posOrder);
                em.flush();

                if (posPaymentChanged) {
                    auditLogService.log(request, "PAYMENT_RECEIVED", "POS",
                            "POS Order #POS-" + String.format("%05d", id) + " marked as FULL PAID | Amount: LKR " + posOrder.getTotalAmount() + " (COD Balance cleared)",
                            "SUCCESS");
                }

                auditLogService.log(request, "UPDATE_POS_ORDER_STATUS", "POS",
                        "POS Order #POS-" + String.format("%05d", id) + " status updated from " + oldOrderStatus + " to " + posOrder.getOrderStatus() + " | Payment: " + posOrder.getPaymentMethod() + " (Total: LKR " + posOrder.getTotalAmount() + ")",
                        "SUCCESS");

                return ResponseEntity.ok("{\"success\":true,\"message\":\"POS Order status updated.\"}");
            }

            if (order == null) return ResponseEntity.status(404).body("{\"success\":false,\"message\":\"Order not found.\"}");
            
            String oldOrderStatus = order.getOrderStatus();
            String oldPaymentStatus = order.getPaymentStatus();
            boolean orderStatusChanged = false;
            boolean paymentStatusChanged = false;

            if (!body.path("orderStatus").isMissingNode()) {
                String newStatus = body.path("orderStatus").asText().toUpperCase();
                if (!newStatus.equals(oldOrderStatus)) {
                    String curUpper = oldOrderStatus != null ? oldOrderStatus.toUpperCase() : "PENDING";
                    if (("DELIVERED".equals(curUpper) || "CANCELLED".equals(curUpper) || "RETURNED".equals(curUpper)) &&
                        ("PENDING".equals(newStatus) || "PROCESSING".equals(newStatus))) {
                        return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"Completed or cancelled orders cannot be reverted back to PENDING/PROCESSING.\"}");
                    }
                    order.setOrderStatus(newStatus);
                    if ("DELIVERED".equals(newStatus)) {
                        order.setPaymentStatus("PAID");
                        paymentStatusChanged = true;
                    }
                    orderStatusChanged = true;
                }
            }
            if (!body.path("paymentStatus").isMissingNode()) {
                String newStatus = body.path("paymentStatus").asText();
                if (!newStatus.equals(oldPaymentStatus)) {
                    order.setPaymentStatus(newStatus);
                    paymentStatusChanged = true;
                    if ("PENDING".equalsIgnoreCase(order.getOrderStatus()) && ("DEPOSIT_PAID".equalsIgnoreCase(newStatus) || "PAID".equalsIgnoreCase(newStatus))) {
                        order.setOrderStatus("PROCESSING");
                        orderStatusChanged = true;
                    }
                }
            }
            em.merge(order);
            em.flush(); // ensure changes are saved before sending email

            if (paymentStatusChanged) {
                String payAction = "DELIVERED".equals(order.getOrderStatus()) && "PAID".equals(order.getPaymentStatus()) 
                        ? "COD_PAYMENT_COLLECTED" 
                        : "UPDATE_PAYMENT_STATUS";
                auditLogService.log(request, payAction, "ORDER",
                        "Web Order #CLC-" + String.format("%05d", order.getId()) + " payment status changed from " + oldPaymentStatus + " to " + order.getPaymentStatus() + " | Order Total: LKR " + order.getTotalAmount() + ("DELIVERED".equals(order.getOrderStatus()) ? " (COD Collected on Delivery)" : ""),
                        "SUCCESS");
            }

            if (orderStatusChanged) {
                auditLogService.log(request, "UPDATE_ORDER_STATUS", "ORDER",
                        "Web Order #CLC-" + String.format("%05d", order.getId()) + " status changed from " + oldOrderStatus + " to " + order.getOrderStatus() + " [Payment: " + order.getPaymentStatus() + "]",
                        "SUCCESS");
            }

            if (orderStatusChanged || paymentStatusChanged) {
                final boolean fOrderStatusChanged = orderStatusChanged;
                final boolean fPaymentStatusChanged = paymentStatusChanged;
                final Order fOrder = order;
                final String fNewStatus = order.getOrderStatus();
                org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            if (fOrderStatusChanged) {
                                try { emailService.sendOrderStatusUpdate(fOrder, oldOrderStatus); } catch (Exception ignored) {}
                                try { 
                                    notificationService.notifyUser(fOrder.getUser().getId(), "ORDER_UPDATE", "Your order #CLC-" + String.format("%05d", fOrder.getId()) + " is now " + fOrder.getOrderStatus(), "/account.html"); 
                                } catch (Exception ignored) {}
                                if ("PROCESSING".equalsIgnoreCase(fNewStatus)) {
                                    try {
                                        notificationService.notifyStaffByRole(java.util.List.of("ADMIN", "MANAGER", "PACKING"), "ORDER_PROCESSING", "Order #CLC-" + String.format("%05d", fOrder.getId()) + " is ready for packing!", "/admin.html");
                                    } catch (Exception ignored) {}
                                }
                            }
                            if (fPaymentStatusChanged) {
                                try { emailService.sendPaymentStatusUpdate(fOrder, oldPaymentStatus); } catch (Exception ignored) {}
                            }
                        }
                    }
                );
            }

            return ResponseEntity.ok("{\"success\":true,\"message\":\"Order status updated.\"}");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
