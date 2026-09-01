package com.auracraft.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * User entity – migrated from Jakarta EE (unchanged JPA annotations).
 * Maps to the `users` table.
 */
@Entity
@Table(name = "users")
public class User implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = true, unique = true, length = 100)
    private String email;

    @Column(nullable = true, length = 255)
    private String password;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(length = 15)
    private String phone;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "profile_image_url", length = 512)
    private String profileImageUrl;

    @Column(nullable = false, length = 255)
    private String role = "CUSTOMER";

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "verification_token", length = 64, unique = true)
    private String verificationToken;

    @Column(name = "verification_token_expiry")
    private LocalDateTime verificationTokenExpiry;

    @Column(name = "password_reset_token", length = 64, unique = true)
    private String passwordResetToken;

    @Column(name = "password_reset_token_expiry")
    private LocalDateTime passwordResetTokenExpiry;

    @Column(name = "is_subscribed", nullable = false)
    private boolean isSubscribed = false;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "auth_provider", nullable = false, length = 50)
    private String authProvider = "LOCAL";

    @Column(name = "provider_id", length = 255)
    private String providerId;

    @Column(name = "daily_sales_target", precision = 10, scale = 2)
    private BigDecimal dailySalesTarget = new BigDecimal("50000.00");

    @Column(name = "daily_orders_target")
    private Integer dailyOrdersTarget = 10;

    @Column(name = "monthly_sales_target", precision = 10, scale = 2)
    private BigDecimal monthlySalesTarget = new BigDecimal("1000000.00");

    // ── Getters & Setters ─────────────────────────────────────────
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public String getProfileImageUrl() { return profileImageUrl; }
    public void setProfileImageUrl(String profileImageUrl) { this.profileImageUrl = profileImageUrl; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }

    public String getVerificationToken() { return verificationToken; }
    public void setVerificationToken(String verificationToken) { this.verificationToken = verificationToken; }

    public LocalDateTime getVerificationTokenExpiry() { return verificationTokenExpiry; }
    public void setVerificationTokenExpiry(LocalDateTime verificationTokenExpiry) {
        this.verificationTokenExpiry = verificationTokenExpiry;
    }

    public String getPasswordResetToken() { return passwordResetToken; }
    public void setPasswordResetToken(String passwordResetToken) { this.passwordResetToken = passwordResetToken; }

    public LocalDateTime getPasswordResetTokenExpiry() { return passwordResetTokenExpiry; }
    public void setPasswordResetTokenExpiry(LocalDateTime passwordResetTokenExpiry) {
        this.passwordResetTokenExpiry = passwordResetTokenExpiry;
    }

    public boolean isSubscribed() { return isSubscribed; }
    public void setSubscribed(boolean subscribed) { this.isSubscribed = subscribed; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public String getAuthProvider() { return authProvider; }
    public void setAuthProvider(String authProvider) { this.authProvider = authProvider; }

    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }

    public BigDecimal getDailySalesTarget() { return dailySalesTarget != null ? dailySalesTarget : new BigDecimal("50000.00"); }
    public void setDailySalesTarget(BigDecimal dailySalesTarget) { this.dailySalesTarget = dailySalesTarget; }

    public Integer getDailyOrdersTarget() { return dailyOrdersTarget != null ? dailyOrdersTarget : 10; }
    public void setDailyOrdersTarget(Integer dailyOrdersTarget) { this.dailyOrdersTarget = dailyOrdersTarget; }

    public BigDecimal getMonthlySalesTarget() { return monthlySalesTarget != null ? monthlySalesTarget : new BigDecimal("1000000.00"); }
    public void setMonthlySalesTarget(BigDecimal monthlySalesTarget) { this.monthlySalesTarget = monthlySalesTarget; }
}
