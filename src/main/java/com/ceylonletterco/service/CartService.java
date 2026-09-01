package com.auracraft.service;

import com.auracraft.entity.CartItem;
import com.auracraft.entity.ProductVariant;
import com.auracraft.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * CartService – migrated from EJB @Stateless to Spring @Service.
 */
@Service
@Transactional
public class CartService {

    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    public List<CartItem> getCartItems(int userId) {
        return em.createQuery(
                "SELECT c FROM CartItem c JOIN FETCH c.productVariant v JOIN FETCH v.product " +
                "WHERE c.user.id = :uid ORDER BY c.addedAt DESC", CartItem.class)
                .setParameter("uid", userId)
                .getResultList();
    }

    public void addOrUpdateCartItem(int userId, int variantId, int quantity, String action,
                                    String engravingText, String customResize) throws Exception {
        User user = em.find(User.class, userId);
        ProductVariant variant = em.find(ProductVariant.class, variantId);
        if (variant == null) throw new Exception("Product variant not found.");

        List<CartItem> existing = em.createQuery(
                "SELECT c FROM CartItem c WHERE c.user.id = :uid AND c.productVariant.id = :vid", CartItem.class)
                .setParameter("uid", userId)
                .setParameter("vid", variantId)
                .getResultList();

        if ("remove".equals(action)) {
            if (!existing.isEmpty()) em.remove(existing.get(0));
            return;
        }

        if (!existing.isEmpty()) {
            CartItem item = existing.get(0);
            if ("sync".equals(action) || "set".equals(action)) {
                item.setQuantity(quantity);
            } else {
                item.setQuantity(item.getQuantity() + quantity);
            }
            if (engravingText != null) item.setEngravingText(engravingText);
            if (customResize != null) item.setCustomResize(customResize);
            em.merge(item);
        } else {
            CartItem item = new CartItem();
            item.setUser(user);
            item.setProductVariant(variant);
            item.setQuantity(quantity);
            item.setEngravingText(engravingText);
            item.setCustomResize(customResize);
            em.persist(item);
        }
    }

    public void removeCartItemByProductOrVariant(int userId, int id) {
        // Try removing by variant ID first, then by cart item ID
        List<CartItem> byVariant = em.createQuery(
                "SELECT c FROM CartItem c WHERE c.user.id = :uid AND c.productVariant.id = :vid", CartItem.class)
                .setParameter("uid", userId)
                .setParameter("vid", id)
                .getResultList();
        if (!byVariant.isEmpty()) {
            em.remove(byVariant.get(0));
            return;
        }
        CartItem item = em.find(CartItem.class, id);
        if (item != null && item.getUser().getId().equals(userId)) {
            em.remove(item);
        }
    }

    public void clearCart(int userId) {
        em.createQuery("DELETE FROM CartItem c WHERE c.user.id = :uid")
                .setParameter("uid", userId)
                .executeUpdate();
    }

    @Transactional(readOnly = true)
    public int resolveVariantId(int productId) {
        List<ProductVariant> variants = em.createQuery(
                "SELECT v FROM ProductVariant v WHERE v.product.id = :pid AND (v.isDeleted = false OR v.isDeleted IS NULL) ORDER BY v.id ASC",
                ProductVariant.class)
                .setParameter("pid", productId)
                .setMaxResults(1)
                .getResultList();
        return variants.isEmpty() ? -1 : variants.get(0).getId();
    }

    @Transactional(readOnly = true)
    public String getPrimaryImageUrl(int productId) {
        List<String> urls = em.createQuery(
                "SELECT i.imageUrl FROM ProductImage i WHERE i.product.id = :pid ORDER BY i.isPrimary DESC, i.id ASC",
                String.class)
                .setParameter("pid", productId)
                .setMaxResults(1)
                .getResultList();
        return urls.isEmpty() ? "" : urls.get(0);
    }
}
