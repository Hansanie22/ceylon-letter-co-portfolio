package com.ceylonletterco.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Order validation filter – migrated from Jakarta EE @WebFilter.
 * Validates that all cart items in a POST /api/orders/place request
 * have quantity >= 1.
 *
 * Uses Spring's OncePerRequestFilter to ensure it runs exactly once per request.
 */
@Component
@Order(2)
public class OrderValidationFilter extends OncePerRequestFilter {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Only intercept POST /api/orders/place
        if ("POST".equalsIgnoreCase(request.getMethod())
                && request.getRequestURI().endsWith("/api/orders/place")) {

            // Wrap request so body can be read multiple times
            CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);
            String body = wrappedRequest.getBody();

            try {
                JsonNode root = objectMapper.readTree(body);
                JsonNode items = root.path("items");
                if (items.isArray()) {
                    for (JsonNode item : items) {
                        int quantity = item.path("quantity").asInt(1);
                        if (quantity < 1) {
                            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                            response.setContentType("application/json");
                            response.getWriter().write(
                                "{\"success\":false,\"message\":\"Invalid quantity specified. Quantity must be at least 1.\"}");
                            return;
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore JSON parse errors – let the controller handle bad input
            }

            filterChain.doFilter(wrappedRequest, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    // ── Inner class: allows reading the request body multiple times ──────────
    static class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

        private final byte[] cachedBody;

        public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
            super(request);
            InputStream inputStream = request.getInputStream();
            this.cachedBody = (inputStream != null)
                    ? inputStream.readAllBytes()
                    : new byte[0];
        }

        public String getBody() {
            return new String(cachedBody, StandardCharsets.UTF_8);
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(cachedBody);
            return new ServletInputStream() {
                @Override public boolean isFinished() { return byteArrayInputStream.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener l) {}
                @Override public int read() throws IOException { return byteArrayInputStream.read(); }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
