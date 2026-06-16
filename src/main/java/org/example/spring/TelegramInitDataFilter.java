package org.example.spring;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class TelegramInitDataFilter extends OncePerRequestFilter {
    @Value("${telegram.bot.token}")
    private String botToken;

    private static final Set<String> PUBLIC_PATHS = Set.of("/health", "/config.js");

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    @NonNull HttpServletResponse res,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        if (PUBLIC_PATHS.contains(req.getRequestURI())) {
            chain.doFilter(req, res);
            return;
        }

        String initData = req.getHeader("X-Telegram-Init-Data");
        if (initData == null || initData.isBlank()) {
            res.sendError(401, "Missing Telegram initData");
            return;
        }

        Long chatId = validateAndExtractChatId(initData);
        if (chatId == null) {
            res.sendError(401, "Invalid Telegram initData");
            return;
        }

        req.setAttribute("verified_chat_id", chatId);
        chain.doFilter(req, res);
    }

    private Long validateAndExtractChatId(String initData) {
        try {
            Map<String, String> params = new LinkedHashMap<>();
            String hash = null;
            for (String part : initData.split("&")) {
                int eq = part.indexOf('=');
                String key = URLDecoder.decode(part.substring(0, eq), StandardCharsets.UTF_8);
                String val = URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
                if (key.equals("hash")) hash = val;
                else params.put(key, val);
            }
            if (hash == null) return null;

            String dataCheckString = params.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining("\n"));

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    "WebAppData".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] secretKey = mac.doFinal(botToken.getBytes(StandardCharsets.UTF_8));

            mac.init(new SecretKeySpec(secretKey, "HmacSHA256"));
            String expected = HexFormat.of().formatHex(
                    mac.doFinal(dataCheckString.getBytes(StandardCharsets.UTF_8)));

            if (!MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    hash.getBytes(StandardCharsets.UTF_8))) {
                return null;
            }

            String authDate = params.get("auth_date");
            if (authDate != null) {
                long age = System.currentTimeMillis() / 1000 - Long.parseLong(authDate);
                if (age > 86400) return null;
            }

            String userJson = params.get("user");
            if (userJson == null) return null;
            JsonNode userNode = new ObjectMapper().readTree(userJson);
            return userNode.get("id").asLong();

        } catch (Exception e) {
            return null;
        }
    }
}