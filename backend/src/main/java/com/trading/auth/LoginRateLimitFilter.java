package com.trading.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limits login attempts to {@value MAX_ATTEMPTS} per minute per client IP.
 * Only applied to {@code POST /api/auth/login}.
 * Returns HTTP 429 with a JSON error body when the limit is exceeded.
 */
@Slf4j
@Component
@Order(1)
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_ATTEMPTS = 10;
    private static final long WINDOW_MS = 60_000;
    private static final String LOGIN_PATH = "/api/auth/login";

    /** Map of IP → [windowStartMs, attemptCount] */
    private final ConcurrentHashMap<String, long[]> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        if ("POST".equalsIgnoreCase(req.getMethod()) && LOGIN_PATH.equals(req.getRequestURI())) {
            String ip = clientIp(req);
            long count = increment(ip);
            if (count > MAX_ATTEMPTS) {
                log.warn("Login rate limit exceeded for IP {}", ip);
                res.setStatus(429);
                res.setContentType("application/json");
                res.getWriter().write("{\"success\":false,\"error\":\"Too many login attempts — try again in a minute.\"}");
                return;
            }
        }
        chain.doFilter(req, res);
    }

    private long increment(String ip) {
        long now = System.currentTimeMillis();
        long[] bucket = buckets.compute(ip, (k, v) -> {
            if (v == null || now - v[0] > WINDOW_MS) {
                return new long[]{now, 1};
            }
            return new long[]{v[0], v[1] + 1};
        });
        return bucket[1];
    }

    private String clientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }

    /** Remove expired buckets every minute to prevent unbounded memory growth. */
    @Scheduled(fixedDelay = 60_000)
    public void cleanup() {
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        buckets.entrySet().removeIf(e -> e.getValue()[0] < cutoff);
    }
}
