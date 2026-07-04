package com.chegg.ratelimiter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    private Map<String, EndpointRule> endpoints = defaultEndpoints();

    public Map<String, EndpointRule> endpoints() {
        return endpoints;
    }

    public void setEndpoints(Map<String, EndpointRule> endpoints) {
        this.endpoints = endpoints;
    }

    private static Map<String, EndpointRule> defaultEndpoints() {
        Map<String, EndpointRule> defaults = new LinkedHashMap<>();
        defaults.put("general", new EndpointRule("/api/general", 20, 60));
        defaults.put("submit", new EndpointRule("/api/submit", 5, 60));
        defaults.put("status", new EndpointRule("/api/status", 60, 60));
        return defaults;
    }

    public record EndpointRule(String path, int limit, int windowSizeInSeconds) {
    }
}
