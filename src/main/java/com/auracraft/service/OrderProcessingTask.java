package com.auracraft.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * OrderProcessingTask – Legacy background task holder.
 * Note: Status transitions from PENDING → PROCESSING are strictly handled by OrderStatusScheduler 
 * with strict payment slip verification rules.
 */
@Component
public class OrderProcessingTask {

    private static final Logger LOG = LoggerFactory.getLogger(OrderProcessingTask.class);

    @PersistenceContext
    private EntityManager em;

    // Disabled to prevent unwanted status overrides. OrderStatusScheduler manages 11 PM auto-processing.
}
