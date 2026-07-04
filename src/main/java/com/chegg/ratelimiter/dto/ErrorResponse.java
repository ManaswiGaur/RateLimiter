package com.chegg.ratelimiter.dto;

public record ErrorResponse(String error, long retryAfterSeconds) {
}
