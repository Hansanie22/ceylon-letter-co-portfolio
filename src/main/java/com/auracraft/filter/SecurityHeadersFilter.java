package com.auracraft.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Security Headers filter – migrated from the original SecurityHeadersFilter.
 * Adds OWASP-recommended security response headers to every HTTP response.
 * Configured to allow:
 *   - Google Fonts & CDN resources
 *   - Inline scripts/styles (required by the existing frontend)
 *   - External images (Cloudinary, Unsplash, Instagram, etc.)
 *   - External media streams (Mixkit, Cloudinary, video CDN)
 *   - API calls to external services (Cloudinary, Firebase, PayHere, etc.)
 */
@Component
@Order(1)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // ── Clickjacking protection ──────────────────────────────────────────
        response.setHeader("X-Frame-Options", "SAMEORIGIN");

        // ── XSS protection ───────────────────────────────────────────────────
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-XSS-Protection", "1; mode=block");

        // ── Referrer policy ──────────────────────────────────────────────────
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        // ── Content Security Policy ──────────────────────────────────────────
        response.setHeader("Content-Security-Policy",
            "default-src 'self' https: data: blob:; " +
            "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://cdn.jsdelivr.net https://www.gstatic.com https://apis.google.com https://www.payhere.lk https://app.payhere.lk https://sandbox.payhere.lk https://www.youtube.com https://www.instagram.com https://www.tiktok.com https://www.facebook.com https://connect.facebook.net; " +
            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com https://cdn.jsdelivr.net; " +
            "font-src 'self' https://fonts.gstatic.com data:; " +
            "img-src 'self' data: blob: https:; " +
            "media-src 'self' data: blob: https: https://res.cloudinary.com https://assets.mixkit.co; " +
            "connect-src 'self' https: https://api.cloudinary.com https://identitytoolkit.googleapis.com https://securetoken.googleapis.com https://nominatim.openstreetmap.org https://www.payhere.lk https://app.payhere.lk https://sandbox.payhere.lk https://www.facebook.com; " +
            "frame-src 'self' https://www.payhere.lk https://app.payhere.lk https://sandbox.payhere.lk https://www.youtube.com https://www.instagram.com https://www.tiktok.com https://www.facebook.com https://web.facebook.com; " +
            "object-src 'none';"
        );

        // ── HSTS (HTTP Strict Transport Security) ───────────────────────────
        if (request.isSecure()) {
            response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }

        filterChain.doFilter(request, response);
    }
}
