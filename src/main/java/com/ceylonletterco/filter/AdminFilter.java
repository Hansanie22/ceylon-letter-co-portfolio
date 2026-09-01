package com.ceylonletterco.filter;

import com.ceylonletterco.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Admin authentication filter – migrated from the original AdminFilter.class.
 * Protects all /api/admin/** endpoints – requires an ADMIN or STAFF role.
 *
 * NOTE: The original AdminServlet itself also checks the role inline, so this
 * filter provides a first-line defence at the Servlet container level.
 */
@Component
@Order(3)
public class AdminFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();

        if (!uri.startsWith(request.getContextPath() + "/api/admin")) {
            // Not an admin endpoint – pass through
            filterChain.doFilter(request, response);
            return;
        }
        HttpSession session = request.getSession(false);
        User user = (session != null) ? (User) session.getAttribute("loggedInUser") : null;

        if (user == null) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"message\":\"Access denied. Please log in.\"}");
            return;
        }

        String role = (user.getRole() != null) ? user.getRole().toUpperCase() : "";
        if ("CUSTOMER".equals(role)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write(
                "{\"success\":false,\"message\":\"Access denied. Administrative privileges required.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
