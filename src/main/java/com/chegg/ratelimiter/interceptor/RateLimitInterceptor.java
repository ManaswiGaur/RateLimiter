package com.chegg.ratelimiter.interceptor;

import com.chegg.ratelimiter.config.RateLimitProperties;
import com.chegg.ratelimiter.dto.ErrorResponse;
import com.chegg.ratelimiter.service.RateLimitResult;
import com.chegg.ratelimiter.service.RateLimiterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Optional;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    public static final String API_KEY_HEADER = "X-API-Key";
    public static final String RATE_LIMIT_LIMIT_HEADER = "X-RateLimit-Limit";
    public static final String RATE_LIMIT_REMAINING_HEADER = "X-RateLimit-Remaining";
    public static final String RATE_LIMIT_RESET_HEADER = "X-RateLimit-Reset";

    private final RateLimitProperties properties;
    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;

    public RateLimitInterceptor(
            RateLimitProperties properties,
            RateLimiterService rateLimiterService,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.rateLimiterService = rateLimiterService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Optional<RateLimitProperties.EndpointRule> rule = properties.endpoints()
                .values()
                .stream()
                .filter(candidate -> candidate.path().equals(request.getRequestURI()))
                .findFirst();

        if (rule.isEmpty()) {
            return true;
        }

        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey == null || apiKey.isBlank()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), new ErrorResponse("Missing X-API-Key header", 0));
            return false;
        }

        RateLimitResult result = rateLimiterService.consume(apiKey, rule.get());
        request.setAttribute(RateLimitResult.REQUEST_ATTRIBUTE, result);
        writeRateLimitHeaders(response, apiKey, result);

        if (result.allowed()) {
            return true;
        }

        writeTooManyRequests(response, result);
        return false;
    }

    private void writeRateLimitHeaders(HttpServletResponse response, String apiKey, RateLimitResult result) {
        response.setHeader(API_KEY_HEADER, apiKey);
        response.setHeader(RATE_LIMIT_LIMIT_HEADER, String.valueOf(result.limit()));
        response.setHeader(RATE_LIMIT_REMAINING_HEADER, String.valueOf(result.remaining()));
        response.setHeader(RATE_LIMIT_RESET_HEADER, String.valueOf(result.resetAt()));
    }

    private void writeTooManyRequests(HttpServletResponse response, RateLimitResult result) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(result.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), new ErrorResponse("Too many requests", result.retryAfterSeconds()));
    }
}
