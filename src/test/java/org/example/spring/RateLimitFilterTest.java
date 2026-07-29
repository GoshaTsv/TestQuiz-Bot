package org.example.spring;

import org.example.bot.RateLimiterManager;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {
    @Test
    void withinLimit_passes() throws Exception {
        RateLimiterManager manager = new RateLimiterManager();
        RateLimitFilter filter = new RateLimitFilter(manager);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/messages");
        req.addHeader("X-Forwarded-For", "1.2.3.4");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void exceedsLimit_returns429() throws Exception {
        RateLimiterManager manager = new RateLimiterManager();
        RateLimitFilter filter = new RateLimitFilter(manager);

        MockHttpServletResponse lastResponse = null;
        for (int i = 0; i < 12; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/messages");
            req.addHeader("X-Forwarded-For", "5.5.5.5");
            lastResponse = new MockHttpServletResponse();
            filter.doFilter(req, lastResponse, new MockFilterChain());
        }

        assertThat(lastResponse.getStatus()).isEqualTo(429);
        assertThat(lastResponse.getHeader("Retry-After")).isEqualTo("1");
    }

    @Test
    void nonApiPath_skipped() throws Exception {
        RateLimiterManager manager = new RateLimiterManager();
        RateLimitFilter filter = new RateLimitFilter(manager);

        for (int i = 0; i < 50; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/health");
            req.addHeader("X-Forwarded-For", "9.9.9.9");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, new MockFilterChain());
            assertThat(res.getStatus()).isEqualTo(200);
        }
    }

    @Test
    void differentIPs_separateLimits() throws Exception {
        RateLimiterManager manager = new RateLimiterManager();
        RateLimitFilter filter = new RateLimitFilter(manager);

        for (int i = 0; i < 11; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/messages");
            req.addHeader("X-Forwarded-For", "10.0.0.1");
            filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        }

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/messages");
        req.addHeader("X-Forwarded-For", "10.0.0.2");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());

        assertThat(res.getStatus()).isEqualTo(200);
    }
}