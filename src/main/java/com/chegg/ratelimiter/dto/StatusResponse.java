package com.chegg.ratelimiter.dto;

public record StatusResponse(int limit, int remaining, long resetAt) {
}
