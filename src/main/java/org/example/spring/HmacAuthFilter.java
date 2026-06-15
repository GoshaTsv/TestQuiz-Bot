package org.example.spring;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;

@Component
public class HmacAuthFilter extends OncePerRequestFilter {
    @Value("${app.hmac-secret}")
    private String secret;

    private static final long MAX_AGE_MS = 30_000;

    private static final Set<String> PUBLIC_PATHS = Set.of("/health");

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    @NonNull HttpServletResponse res,
                                    @NonNull FilterChain chain) throws ServletException, IOException {

        if (PUBLIC_PATHS.contains(req.getRequestURI())) {
            chain.doFilter(req, res);
            return;
        }

        String timestamp = req.getHeader("X-Timestamp");
        String signature = req.getHeader("X-Signature");
        String chatIdHeader = req.getHeader("X-Chat-Id");

        if (timestamp == null || signature == null || chatIdHeader == null) {
            res.sendError(401, "Missing auth headers");
            return;
        }

        try {
            long ts = Long.parseLong(timestamp);
            if (Math.abs(System.currentTimeMillis() - ts) > MAX_AGE_MS) {
                res.sendError(401, "Request expired");
                return;
            }
        } catch (NumberFormatException e) {
            res.sendError(401, "Invalid timestamp");
            return;
        }

        CachedBodyHttpServletRequest cachedReq = new CachedBodyHttpServletRequest(req);
        String body = new String(cachedReq.getBody(), StandardCharsets.UTF_8);

        String expected = computeHmac(timestamp + chatIdHeader + body, secret);
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8))) {
            res.sendError(401, "Invalid signature");
            return;
        }

        req.setAttribute("verified_chat_id", Long.parseLong(chatIdHeader));
        chain.doFilter(cachedReq, res);
    }

    private String computeHmac(String data, String secret) throws IOException {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(
                    mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IOException("HMAC computation failed", e);
        }
    }
}
