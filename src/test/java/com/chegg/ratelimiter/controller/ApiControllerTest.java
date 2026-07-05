package com.chegg.ratelimiter.controller;

import com.chegg.ratelimiter.interceptor.RateLimitInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requestWithinLimitPassesWithRateLimitHeaders() throws Exception {
        String apiKey = "within-limit";

        mockMvc.perform(get("/api/general").header(RateLimitInterceptor.API_KEY_HEADER, apiKey))
                .andExpect(status().isOk())
                .andExpect(header().string(RateLimitInterceptor.API_KEY_HEADER, apiKey))
                .andExpect(header().string(RateLimitInterceptor.RATE_LIMIT_LIMIT_HEADER, "20"))
                .andExpect(header().string(RateLimitInterceptor.RATE_LIMIT_REMAINING_HEADER, "19"))
                .andExpect(header().exists(RateLimitInterceptor.RATE_LIMIT_RESET_HEADER))
                .andExpect(jsonPath("$.message").value("OK"));
    }

    @Test
    void exactLimitPassesAndNextRequestReturns429WithRetryAfter() throws Exception {
        String apiKey = "general-boundary";

        for (int i = 0; i < 20; i++) {
            mockMvc.perform(get("/api/general").header(RateLimitInterceptor.API_KEY_HEADER, apiKey))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/general").header(RateLimitInterceptor.API_KEY_HEADER, apiKey))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(RateLimitInterceptor.RATE_LIMIT_LIMIT_HEADER, "20"))
                .andExpect(header().string(RateLimitInterceptor.RATE_LIMIT_REMAINING_HEADER, "0"))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, not("0")))
                .andExpect(jsonPath("$.error").value("Too many requests"))
                .andExpect(jsonPath("$.retryAfterSeconds", greaterThanOrEqualTo(1)));
    }

    @Test
    void twoApiKeysHaveIndependentCounters() throws Exception {
        String blockedKey = "blocked-submit-client";
        String otherKey = "fresh-submit-client";

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/submit").header(RateLimitInterceptor.API_KEY_HEADER, blockedKey))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/submit").header(RateLimitInterceptor.API_KEY_HEADER, blockedKey))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(post("/api/submit").header(RateLimitInterceptor.API_KEY_HEADER, otherKey))
                .andExpect(status().isOk())
                .andExpect(header().string(RateLimitInterceptor.RATE_LIMIT_REMAINING_HEADER, "4"));
    }

    @Test
    void submitLimitIsIndependentFromGeneralLimit() throws Exception {
        String apiKey = "endpoint-independent";

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/submit").header(RateLimitInterceptor.API_KEY_HEADER, apiKey))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/submit").header(RateLimitInterceptor.API_KEY_HEADER, apiKey))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(get("/api/general").header(RateLimitInterceptor.API_KEY_HEADER, apiKey))
                .andExpect(status().isOk())
                .andExpect(header().string(RateLimitInterceptor.RATE_LIMIT_LIMIT_HEADER, "20"))
                .andExpect(header().string(RateLimitInterceptor.RATE_LIMIT_REMAINING_HEADER, "19"));
    }

    @Test
    void statusEndpointReturnsCurrentRateLimitState() throws Exception {
        String apiKey = "status-client";

        mockMvc.perform(get("/api/status").header(RateLimitInterceptor.API_KEY_HEADER, apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limit").value(60))
                .andExpect(jsonPath("$.remaining").value(59))
                .andExpect(jsonPath("$.resetAt").isNumber());
    }

    @Test
    void missingApiKeyReturns400() throws Exception {
        mockMvc.perform(get("/api/general"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing X-API-Key header"));
    }
}
