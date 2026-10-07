package com.truthscan.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Protects /api/admin/** with a shared secret sent in the "X-Admin-Key" header.
 * Simple on purpose; replace with Spring Security user accounts when you add logins.
 */
@Component
public class AdminKeyInterceptor implements HandlerInterceptor {

    private final String adminKey;

    public AdminKeyInterceptor(@Value("${app.admin-key:}") String adminKey) {
        this.adminKey = adminKey;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String provided = request.getHeader("X-Admin-Key");

        if (adminKey.isBlank() || provided == null || !constantTimeEquals(adminKey, provided)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"code\":\"UNAUTHORIZED\",\"message\":\"Missing or wrong admin key\",\"details\":[]}");
            return false;
        }
        return true;
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
