package com.auracraft.service;

import com.auracraft.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * AuthService – migrated from EJB @Stateless to Spring @Service.
 *
 * All method signatures are identical to the original EJB.
 * @EJB → @Autowired, @PersistenceContext → EntityManager via Spring JPA,
 * @Resource UserTransaction → Spring @Transactional.
 */
@Service
@Transactional
public class AuthService {

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private EmailVerificationService emailVerificationService;

    // ── User Registration ──────────────────────────────────────────────────
    public void registerUser(String name, String email, String password,
                             String appBaseUrl, String returnUrl) throws Exception {
        // Check if email already exists
        List<User> existing = em.createQuery(
                "SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email)", User.class)
                .setParameter("email", email)
                .getResultList();
        if (!existing.isEmpty()) {
            throw new Exception("An account with this email already exists.");
        }

        String hashed = BCrypt.hashpw(password, BCrypt.gensalt(12));
        String token = UUID.randomUUID().toString().replace("-", "");

        User user = new User();
        user.setFullName(name);
        user.setEmail(email.toLowerCase().trim());
        user.setPassword(hashed);
        user.setRole("CUSTOMER");
        user.setEmailVerified(false);
        user.setVerificationToken(token);
        user.setVerificationTokenExpiry(java.time.LocalDateTime.now().plusHours(24));
        em.persist(user);
        em.flush();

        // Send verification email asynchronously
        emailVerificationService.sendVerificationEmail(user, appBaseUrl, returnUrl);
    }

    // ── Authentication ─────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public User authenticate(String email, String password) throws Exception {
        List<User> users = em.createQuery(
                "SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email)", User.class)
                .setParameter("email", email.trim())
                .getResultList();

        if (users.isEmpty()) return null;
        User user = users.get(0);

        if (!user.isActive()) {
            throw new Exception("ACCOUNT_DISABLED");
        }

        // Universal check: All users must be verified to login (unless they use Google/Phone which auto-verifies)
        if (!user.isEmailVerified() && "LOCAL".equals(user.getAuthProvider())) {
            throw new Exception("EMAIL_NOT_VERIFIED");
        }

        if (user.getPassword() == null || !BCrypt.checkpw(password, user.getPassword())) {
            return null;
        }

        return user;
    }

    // ── Profile Update ─────────────────────────────────────────────────────
    public User updateProfile(Integer userId, String fullName, String phone, String dob) throws Exception {
        User user = em.find(User.class, userId);
        if (user == null) throw new Exception("User not found.");
        user.setFullName(fullName);
        user.setPhone(phone);
        if (dob != null && !dob.isBlank()) {
            user.setDateOfBirth(LocalDate.parse(dob));
        } else {
            user.setDateOfBirth(null);
        }
        return em.merge(user);
    }

    // ── Password Change ────────────────────────────────────────────────────
    public void changePassword(Integer userId, String currentPassword, String newPassword) throws Exception {
        User user = em.find(User.class, userId);
        if (user == null) throw new Exception("User not found.");
        if (user.getPassword() == null || !BCrypt.checkpw(currentPassword, user.getPassword())) {
            throw new Exception("Current password is incorrect.");
        }
        user.setPassword(BCrypt.hashpw(newPassword, BCrypt.gensalt(12)));
        em.merge(user);
    }

    // ── Subscription ────────────────────────────────────────────────────────
    public void subscribeUser(String email) throws Exception {
        List<User> users = em.createQuery(
                "SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email)", User.class)
                .setParameter("email", email.trim())
                .getResultList();
        if (users.isEmpty()) throw new Exception("No account found with that email address.");
        User user = users.get(0);
        user.setSubscribed(true);
        em.merge(user);
    }

    // ── Phone Check ────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public boolean checkPhoneExists(String phone) {
        List<User> users = em.createQuery(
                "SELECT u FROM User u WHERE u.phone = :phone", User.class)
                .setParameter("phone", phone.trim())
                .getResultList();
        return !users.isEmpty();
    }

    // ── Phone Registration ─────────────────────────────────────────────────
    public User registerPhoneUser(String phone, String fullName, String pin) throws Exception {
        if (checkPhoneExists(phone)) {
            throw new Exception("An account with this phone number already exists.");
        }
        String hashedPin = BCrypt.hashpw(pin, BCrypt.gensalt(12));
        User user = new User();
        user.setPhone(phone);
        user.setFullName(fullName);
        user.setPassword(hashedPin);
        user.setRole("CUSTOMER");
        user.setEmailVerified(true);
        user.setAuthProvider("PHONE");
        em.persist(user);
        em.flush();
        return user;
    }

    // ── Phone Login ────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public User loginPhoneUser(String phone, String pin) throws Exception {
        List<User> users = em.createQuery(
                "SELECT u FROM User u WHERE u.phone = :phone", User.class)
                .setParameter("phone", phone.trim())
                .getResultList();
        if (users.isEmpty()) return null;
        User user = users.get(0);
        if (!user.isActive()) {
            throw new Exception("ACCOUNT_DISABLED");
        }

        if (user.getPassword() == null || !BCrypt.checkpw(pin, user.getPassword())) {
            throw new Exception("Invalid PIN.");
        }
        return user;
    }

    // ── OTP / Firebase Login-or-Register ──────────────────────────────────
    public User loginOrRegisterOtpUser(String phone) throws Exception {
        List<User> users = em.createQuery(
                "SELECT u FROM User u WHERE u.phone = :phone", User.class)
                .setParameter("phone", phone.trim())
                .getResultList();
        if (!users.isEmpty()) {
            User user = users.get(0);
            if (!user.isActive()) throw new Exception("ACCOUNT_DISABLED");
            return user;
        } // New user via OTP

        // New user via OTP
        User user = new User();
        user.setPhone(phone);
        user.setFullName("User " + phone.replaceAll("[^0-9]", "").substring(Math.max(0, phone.length() - 4)));
        user.setRole("CUSTOMER");
        user.setEmailVerified(true);
        user.setAuthProvider("FIREBASE_OTP");
        em.persist(user);
        em.flush();
        return user;
    }

    // ── OAuth (Google) Login-or-Register ──────────────────────────────────
    public User loginOrRegisterOAuthUser(String email, String name, String provider,
                                          String providerId, String pictureUrl) throws Exception {
        // Try to find by provider + providerId first
        List<User> byProvider = em.createQuery(
                "SELECT u FROM User u WHERE u.authProvider = :prov AND u.providerId = :pid", User.class)
                .setParameter("prov", provider)
                .setParameter("pid", providerId)
                .getResultList();
        if (!byProvider.isEmpty()) return byProvider.get(0);

        // Try to find by email
        List<User> byEmail = em.createQuery(
                "SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email)", User.class)
                .setParameter("email", email)
                .getResultList();
        if (!byEmail.isEmpty()) {
            User user = byEmail.get(0);
            user.setAuthProvider(provider);
            user.setProviderId(providerId);
            user.setEmailVerified(true);
            if (pictureUrl != null && !pictureUrl.isBlank() && user.getProfileImageUrl() == null) {
                user.setProfileImageUrl(pictureUrl);
            }
            return em.merge(user);
        }

        // New OAuth user
        User user = new User();
        user.setEmail(email.toLowerCase().trim());
        user.setFullName(name);
        user.setRole("CUSTOMER");
        user.setEmailVerified(true);
        user.setAuthProvider(provider);
        user.setProviderId(providerId);
        if (pictureUrl != null && !pictureUrl.isBlank()) user.setProfileImageUrl(pictureUrl);
        em.persist(user);
        em.flush();
        return user;
    }
}
