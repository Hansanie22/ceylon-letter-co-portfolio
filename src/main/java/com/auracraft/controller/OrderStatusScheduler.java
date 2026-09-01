package com.auracraft.controller;

import com.auracraft.entity.Order;
import com.auracraft.entity.Payment;
import com.auracraft.entity.PosOrder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * OrderStatusScheduler – Runs daily at 11:00 PM.
 * Automatically transitions PENDING & CONFIRMED orders → PROCESSING.
 * 
 * Strict Business Rule:
 * For prepaid or advance payment methods (Full Paid, Adv+COD, Bank Transfer, Deposit),
 * orders are ONLY auto-transitioned IF paymentStatus = 'PAID' (payment slip verified by admin).
 * Orders with unverified payment slips or customized items are SKIPPED.
 */
@Component
public class OrderStatusScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(OrderStatusScheduler.class);

    @PersistenceContext
    private EntityManager em;

    @Scheduled(cron = "0 0 23 * * *")
    @Transactional
    public void autoProcessOrdersAt11PM() {
        LOG.info("[Scheduler] Running strict 11:00 PM auto-process for PENDING & CONFIRMED orders...");

        // 1. Website Orders (PENDING or CONFIRMED)
        List<Order> webOrders = em.createQuery(
                "SELECT o FROM Order o WHERE o.orderStatus IN ('PENDING', 'CONFIRMED')", Order.class)
                .getResultList();
        
        int webCount = 0;
        int webSkippedUnverified = 0;
        int webSkippedCustom = 0;

        for (Order o : webOrders) {
            // Check customized items
            Long customCount = em.createQuery(
                    "SELECT COUNT(oi) FROM OrderItem oi WHERE oi.order.id = :oid " +
                    "AND ((oi.engravingText IS NOT NULL AND oi.engravingText <> '') " +
                    "OR (oi.customResize IS NOT NULL AND oi.customResize <> ''))", Long.class)
                    .setParameter("oid", o.getId()).getSingleResult();

            if (customCount > 0) {
                LOG.info("[Scheduler] Web Order #{} has customized items. Skipping auto-transition to PROCESSING.", o.getId());
                webSkippedCustom++;
                continue;
            }

            // Get payment method and status from Payment table
            List<Payment> pmts = em.createQuery(
                    "SELECT p FROM Payment p WHERE p.order.id = :oid ORDER BY p.id DESC", Payment.class)
                    .setParameter("oid", o.getId()).getResultList();

            String pMethod = !pmts.isEmpty() && pmts.get(0).getPaymentMethod() != null ? pmts.get(0).getPaymentMethod().toUpperCase() : "COD";
            String pStatus = o.getPaymentStatus() != null ? o.getPaymentStatus().toUpperCase() : "PENDING";
            if (!pmts.isEmpty() && pmts.get(0).getPaymentStatus() != null) {
                pStatus = pmts.get(0).getPaymentStatus().toUpperCase();
            }

            boolean isPrepaidOrAdvance = pMethod.contains("FULL") ||
                                         pMethod.contains("ADVANCE") ||
                                         pMethod.contains("DEPOSIT") ||
                                         pMethod.contains("BANK") ||
                                         pMethod.contains("PAYHERE") ||
                                         pMethod.contains("CARD");

            if (isPrepaidOrAdvance) {
                boolean isVerifiedPaid = "PAID".equals(pStatus) || "SUCCESS".equals(pStatus) || "COMPLETED".equals(pStatus);
                if (!isVerifiedPaid) {
                    LOG.info("[Scheduler] Web Order #{} paymentMethod='{}' has unverified paymentStatus='{}'. Skipping auto-transition.",
                            o.getId(), pMethod, pStatus);
                    webSkippedUnverified++;
                    continue; // STRICT REQUIREMENT: Only verified payment slips move to PROCESSING
                }
            }

            // Valid non-custom, verified (or pure COD) order -> transition to PROCESSING
            o.setOrderStatus("PROCESSING");
            em.merge(o);
            webCount++;
        }

        // 2. POS Orders (PENDING or CONFIRMED)
        List<PosOrder> posOrders = em.createQuery(
                "SELECT po FROM PosOrder po WHERE po.orderStatus IN ('PENDING', 'CONFIRMED')", PosOrder.class)
                .getResultList();
        
        int posCount = 0;
        int posSkippedUnverified = 0;
        int posSkippedCustom = 0;

        for (PosOrder po : posOrders) {
            boolean isCustom = (po.getIsCustom() != null && po.getIsCustom()) ||
                               (po.getCustomNotes() != null && !po.getCustomNotes().trim().isEmpty());

            if (isCustom) {
                LOG.info("[Scheduler] POS Order #{} is customized. Skipping auto-transition to PROCESSING.", po.getId());
                posSkippedCustom++;
                continue;
            }

            String pMethod = po.getPaymentMethod() != null ? po.getPaymentMethod().toUpperCase() : "COD";
            boolean isPrepaidOrAdvance = pMethod.contains("FULL") ||
                                         pMethod.contains("ADVANCE") ||
                                         pMethod.contains("DEPOSIT") ||
                                         pMethod.contains("BANK");

            if (isPrepaidOrAdvance) {
                BigDecimal advPaid = po.getAdvancePaid() != null ? po.getAdvancePaid() : BigDecimal.ZERO;
                BigDecimal codBal = po.getCodBalance() != null ? po.getCodBalance() : BigDecimal.ZERO;
                boolean isPaidOrDeposited = (advPaid.compareTo(BigDecimal.ZERO) > 0) || (codBal.compareTo(BigDecimal.ZERO) <= 0);

                if (!isPaidOrDeposited) {
                    LOG.info("[Scheduler] POS Order #{} requires advance payment verification. Skipping auto-transition.", po.getId());
                    posSkippedUnverified++;
                    continue;
                }
            }

            po.setOrderStatus("PROCESSING");
            em.merge(po);
            posCount++;
        }

        LOG.info("[Scheduler] 11:00 PM Auto-process summary: {} Web + {} POS moved to PROCESSING. Skipped {} unverified payments, {} custom orders.",
                webCount, posCount, (webSkippedUnverified + posSkippedUnverified), (webSkippedCustom + posSkippedCustom));
    }
}
