package com.chegg.ratelimiter.service;

import com.chegg.ratelimiter.config.RateLimitProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class RateLimiterService {

    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiterService(Clock clock) {
        this.clock = clock;
    }

    public RateLimitResult consume(String clientId, RateLimitProperties.EndpointRule rule) {
        long now = clock.instant().getEpochSecond();
        long windowStart = now - (now % rule.windowSizeInSeconds());
        long resetAt = windowStart + rule.windowSizeInSeconds();
        String key = rule.path() + ":" + clientId;

        AtomicReference<RateLimitResult> result = new AtomicReference<>();
        counters.compute(key, (ignored, current) -> {
            WindowCounter counter = current;
            if (counter == null || counter.windowStart != windowStart) {
                counter = new WindowCounter(windowStart, resetAt, 0);
            }

            if (counter.count < rule.limit()) {
                counter.count++;
                int remaining = rule.limit() - counter.count;
                result.set(new RateLimitResult(true, rule.limit(), remaining, resetAt, 0));
            } else {
                long retryAfterSeconds = Math.max(1, resetAt - now);
                result.set(new RateLimitResult(false, rule.limit(), 0, resetAt, retryAfterSeconds));
            }

            return counter;
        });

        return result.get();
    }

    @Scheduled(fixedDelayString = "${rate-limit.cleanup-interval-ms:60000}")
    public void cleanupExpiredWindows() {
        long now = clock.instant().getEpochSecond();
        counters.entrySet().removeIf(entry -> entry.getValue().resetAt <= now);
    }

    public int trackedClientWindows() {
        return counters.size();
    }

    private static final class WindowCounter {
        private final long windowStart;
        private final long resetAt;
        private int count;

        private WindowCounter(long windowStart, long resetAt, int count) {
            this.windowStart = windowStart;
            this.resetAt = resetAt;
            this.count = count;
        }
    }
}
