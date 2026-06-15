package org.example.bot;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.local.LocalBucket;
import io.github.bucket4j.Refill;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimiterManager {
    private final Map<Long, Bucket> telegramCache = new ConcurrentHashMap<>();
    private final Map<String, Bucket> restCache = new ConcurrentHashMap<>();

    private Bucket createTelegramBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(3, Refill.intervally(3, Duration.ofSeconds(2))))
                .addLimit(Bandwidth.classic(30, Refill.intervally(30, Duration.ofMinutes(1))))
                .build();
    }

    private Bucket createRestBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(10, Refill.intervally(10, Duration.ofSeconds(1))))
                .addLimit(Bandwidth.classic(100, Refill.intervally(100, Duration.ofMinutes(1))))
                .build();
    }

    public boolean tryConsumeTelegram(long userId) {
        return telegramCache.computeIfAbsent(userId, k -> createTelegramBucket()).tryConsume(1);
    }

    public boolean tryConsumeRest(String ip) {
        return restCache.computeIfAbsent(ip, k -> createRestBucket()).tryConsume(1);
    }

    @Scheduled(fixedDelay = 60_000)
    public void cleanup() {
        telegramCache.entrySet().removeIf(e -> {
            if (e.getValue() instanceof LocalBucket localBucket) {
                long maxCapacity = localBucket.getConfiguration().getBandwidths()[0].getCapacity();
                return localBucket.getAvailableTokens() == maxCapacity;
            }
            return false;
        });
        restCache.entrySet().removeIf(e -> {
            if (e.getValue() instanceof LocalBucket lb) {
                long max = lb.getConfiguration().getBandwidths()[0].getCapacity();
                return lb.getAvailableTokens() == max;
            }
            return false;
        });
    }
}
