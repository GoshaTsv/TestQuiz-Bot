package org.example.bot;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimiterManager {
    private final Map<Long, Bucket> cache = new ConcurrentHashMap<>();

    private Bucket createNewBucket() {
        Bandwidth shortTermLimit = Bandwidth.classic(3, Refill.intervally(3, Duration.ofSeconds(1)));
        Bandwidth longTermLimit = Bandwidth.classic(20, Refill.intervally(1, Duration.ofSeconds(3)));

        return Bucket.builder()
                .addLimit(shortTermLimit)
                .addLimit(longTermLimit)
                .build();
    }

    public boolean tryConsume(long userId) {
        return cache.computeIfAbsent(userId, k -> createNewBucket()).tryConsume(1);
    }
}
