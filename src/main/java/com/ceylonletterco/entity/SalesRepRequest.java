package com.ceylonletterco.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * SalesRepRequest – notes/requests submitted by Sales Reps to Admin.
 * e.g. "Customer wants 18 inch chain — currently out of stock"
 */
@Entity
@Table(name = "sales_rep_requests")
public class SalesRepRequest implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    // OUT_OF_STOCK | CUSTOM_REQUEST | OTHER
    @Column(name = "request_type", nullable = false, length = 30)
    private String requestType = "OTHER";

    @Column(name = "product_reference", length = 200)
    private String productReference;

    @Column(name = "notes", columnDefinition = "TEXT", nullable = false)
    private String notes;

    // PENDING | ACKNOWLEDGED | RESOLVED
    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "admin_reply", columnDefinition = "TEXT")
    private String adminReply;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    private User resolvedBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }

    // ── Getters & Setters ─────────────────────────────────────────
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public User getRequester() { return requester; }
    public void setRequester(User requester) { this.requester = requester; }

    public String getRequestType() { return requestType; }
    public void setRequestType(String requestType) { this.requestType = requestType; }

    public String getProductReference() { return productReference; }
    public void setProductReference(String productReference) { this.productReference = productReference; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAdminReply() { return adminReply; }
    public void setAdminReply(String adminReply) { this.adminReply = adminReply; }

    public User getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(User resolvedBy) { this.resolvedBy = resolvedBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
}
