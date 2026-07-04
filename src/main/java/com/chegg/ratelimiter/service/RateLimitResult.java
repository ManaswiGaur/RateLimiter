package com.chegg.ratelimiter.service;

public record RateLimitResult(
        boolean allowed,
        int limit,
        int remaining,
        long resetAt,
        long retryAfterSeconds
) {
    public static final String REQUEST_ATTRIBUTE = RateLimitResult.class.getName();
}
