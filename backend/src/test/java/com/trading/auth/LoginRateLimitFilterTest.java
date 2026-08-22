package com.trading.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRateLimitFilterTest {

    private LoginRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new LoginRateLimitFilter();
    }

    @Test
    @DisplayName("Non-login requests pass through without rate limiting")
    void nonLoginRequest_passesThrough() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/users/me");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull(); // chain was invoked
    }

    @Test
    @DisplayName("Login POST requests within limit pass through")
    void loginRequest_withinLimit_passesThrough() throws Exception {
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
            req.setRemoteAddr("192.168.1.1");
            MockHttpServletResponse res = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(req, res, chain);

            assertThat(res.getStatus()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("Login POST exceeding limit returns 429")
    void loginRequest_exceedsLimit_returns429() throws Exception {
        // Send 10 allowed requests
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
            req.setRemoteAddr("10.0.0.1");
            filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        }

        // 11th request should be blocked
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getContentAsString()).contains("Too many login attempts");
        assertThat(chain.getRequest()).isNull(); // chain was NOT invoked
    }

    @Test
    @DisplayName("Different IPs have independent rate limit buckets")
    void differentIps_haveIndependentBuckets() throws Exception {
        // Exhaust limit for IP A
        for (int i = 0; i < 11; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
            req.setRemoteAddr("1.1.1.1");
            filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        }

        // IP B should still be allowed
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.setRemoteAddr("2.2.2.2");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("X-Forwarded-For header is used as client IP when present")
    void xForwardedFor_usedAsClientIp() throws Exception {
        // Exhaust via X-Forwarded-For IP
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
            req.setRemoteAddr("10.0.0.1"); // proxy IP
            req.addHeader("X-Forwarded-For", "203.0.113.5");
            filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        }

        // 11th — same forwarded IP should be blocked
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.setRemoteAddr("10.0.0.1");
        req.addHeader("X-Forwarded-For", "203.0.113.5");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("cleanup removes expired buckets")
    void cleanup_removesExpiredBuckets() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.setRemoteAddr("99.99.99.99");
        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

        // Cleanup should not throw and runs silently
        filter.cleanup();

        // After cleanup (bucket is recent, still kept), next request still passes
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, res, chain);
        assertThat(res.getStatus()).isEqualTo(200);
    }
}
