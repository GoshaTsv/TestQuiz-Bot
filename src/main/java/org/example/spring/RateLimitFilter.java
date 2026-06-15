package org.example.spring;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.bot.RateLimiterManager;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private final RateLimiterManager rateLimiter;

    public RateLimitFilter(RateLimiterManager rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    @NonNull HttpServletResponse res,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String ip = req.getHeader("X-Forwarded-For");
        if (ip != null && ip.contains(","))
            ip = ip.split(",")[0].trim();
        if (ip == null)
            ip = req.getRemoteAddr();

        if (!rateLimiter.tryConsumeRest(ip)) {
            res.setHeader("Retry-After", "1");
            res.sendError(429, "Too Many Requests");
            return;
        }

        chain.doFilter(req, res);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        return !req.getRequestURI().startsWith("/api/");
    }
}