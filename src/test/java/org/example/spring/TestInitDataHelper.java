// src/test/java/org/example/spring/TestInitDataHelper.java
package org.example.spring;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

public class TestInitDataHelper {
    public static String generateValidInitData(String botToken, long userId, long authDate) throws Exception {
        String userJson = URLEncoder.encode(
            "{\"id\":" + userId + ",\"first_name\":\"Test\",\"username\":\"testuser\"}",
            StandardCharsets.UTF_8
        );

        String dataCheckString = "auth_date=" + authDate + "\nuser=" +
            "{\"id\":" + userId + ",\"first_name\":\"Test\",\"username\":\"testuser\"}";

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("WebAppData".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] secretKey = mac.doFinal(botToken.getBytes(StandardCharsets.UTF_8));

        mac.init(new SecretKeySpec(secretKey, "HmacSHA256"));
        String hash = HexFormat.of().formatHex(
            mac.doFinal(dataCheckString.getBytes(StandardCharsets.UTF_8))
        );

        return "auth_date=" + authDate +
               "&user=" + userJson +
               "&hash=" + hash;
    }

    public static String generateExpiredInitData(String botToken, long userId) throws Exception {
        long expiredDate = System.currentTimeMillis() / 1000 - 172800;
        return generateValidInitData(botToken, userId, expiredDate);
    }

    public static String generateFreshInitData(String botToken, long userId) throws Exception {
        long now = System.currentTimeMillis() / 1000;
        return generateValidInitData(botToken, userId, now);
    }
}