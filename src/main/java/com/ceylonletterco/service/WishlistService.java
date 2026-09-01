package com.auracraft.service;

import com.auracraft.entity.ProductVariant;
import com.auracraft.entity.User;
import com.auracraft.entity.WishlistItem;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * WishlistService – migrated from EJB @Stateless to Spring @Service.
 */
@Service
@Transactional
public class WishlistService {

    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    public List<WishlistItem> getWishlistItems(int userId) {
        return em.createQuery(
                "SELECT w FROM WishlistItem w JOIN FETCH w.productVariant v JOIN FETCH v.product " +
                "WHERE w.user.id = :uid ORDER BY w.addedAt DESC", WishlistItem.class)
                .setParameter("uid", userId)
                .getResultList();
    }

    public void addWishlistItem(int userId, int productId) throws Exception {
        // Resolve the first non-deleted variant for this product
        List<ProductVariant> variants = em.createQuery(
                "SELECT v FROM ProductVariant v WHERE v.product.id = :pid AND (v.isDeleted = false OR v.isDeleted IS NULL) ORDER BY v.id ASC",
                ProductVariant.class)
                .setParameter("pid", productId)
                .setMaxResults(1)
                .getResultList();
        if (variants.isEmpty()) throw new Exception("Product not found.");
        ProductVariant variant = variants.get(0);

        // Check if already in wishlist
        List<WishlistItem> existing = em.createQuery(
                "SELECT w FROM WishlistItem w WHERE w.user.id = :uid AND w.productVariant.product.id = :pid",
                WishlistItem.class)
                .setParameter("uid", userId)
                .setParameter("pid", productId)
                .getResultList();
        if (!existing.isEmpty()) return; // Already exists – no-op

        User user = em.find(User.class, userId);
        WishlistItem item = new WishlistItem();
        item.setUser(user);
        item.setProductVariant(variant);
        em.persist(item);
    }

    public void removeWishlistItem(int userId, int productId) {
        em.createQuery("DELETE FROM WishlistItem w WHERE w.user.id = :uid AND w.productVariant.product.id = :pid")
                .setParameter("uid", userId)
                .setParameter("pid", productId)
                .executeUpdate();
    }

    public void clearWishlist(int userId) {
        em.createQuery("DELETE FROM WishlistItem w WHERE w.user.id = :uid")
                .setParameter("uid", userId)
                .executeUpdate();
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
