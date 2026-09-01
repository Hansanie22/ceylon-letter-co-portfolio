package com.auracraft.controller;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Enumeration;

@RestController
public class DebugController {
    @GetMapping("/debug-request")
    public String debug(HttpServletRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("URI: ").append(request.getRequestURI()).append("\n");
        sb.append("ContextPath: ").append(request.getContextPath()).append("\n");
        sb.append("ServletPath: ").append(request.getServletPath()).append("\n");
        sb.append("PathInfo: ").append(request.getPathInfo()).append("\n");
        sb.append("QueryString: ").append(request.getQueryString()).append("\n");
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            sb.append("Header ").append(name).append(": ").append(request.getHeader(name)).append("\n");
        }
        return sb.toString();
    }
}
