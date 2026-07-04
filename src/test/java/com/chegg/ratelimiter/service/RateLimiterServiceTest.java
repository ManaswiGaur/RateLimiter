package com.chegg.ratelimiter.service;

import com.chegg.ratelimiter.config.RateLimitProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterServiceTest {

    private final MutableClock clock = new MutableClock(Instant.ofEpochSecond(1_750_000_000));
    private final RateLimiterService service = new RateLimiterService(clock);
    private final RateLimitProperties.EndpointRule rule = new RateLimitProperties.EndpointRule("/api/test", 2, 10);

    @Test
    void exactLimitPassesAndNextRequestIsBlocked() {
        RateLimitResult first = service.consume("client-a", rule);
        RateLimitResult second = service.consume("client-a", rule);
        RateLimitResult third = service.consume("client-a", rule);

        assertThat(first.allowed()).isTrue();
        assertThat(first.remaining()).isEqualTo(1);
        assertThat(second.allowed()).isTrue();
        assertThat(second.remaining()).isZero();
        assertThat(third.allowed()).isFalse();
        assertThat(third.retryAfterSeconds()).isPositive();
    }

    @Test
    void differentClientsHaveIndependentCounters() {
        service.consume("client-a", rule);
        service.consume("client-a", rule);
        RateLimitResult blocked = service.consume("client-a", rule);
        RateLimitResult otherClient = service.consume("client-b", rule);

        assertThat(blocked.allowed()).isFalse();
        assertThat(otherClient.allowed()).isTrue();
        assertThat(otherClient.remaining()).isEqualTo(1);
    }

    @Test
    void clientCanRequestAgainAfterWindowResets() {
        service.consume("client-a", rule);
        service.consume("client-a", rule);
        assertThat(service.consume("client-a", rule).allowed()).isFalse();

        clock.setInstant(Instant.ofEpochSecond(1_750_000_010));

        RateLimitResult afterReset = service.consume("client-a", rule);
        assertThat(afterReset.allowed()).isTrue();
        assertThat(afterReset.remaining()).isEqualTo(1);
    }

    @Test
    void cleanupRemovesExpiredWindows() {
        service.consume("client-a", rule);
        assertThat(service.trackedClientWindows()).isEqualTo(1);

        clock.setInstant(Instant.ofEpochSecond(1_750_000_010));
        service.cleanupExpiredWindows();

        assertThat(service.trackedClientWindows()).isZero();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
