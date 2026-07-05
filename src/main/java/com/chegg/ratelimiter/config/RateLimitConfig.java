package com.chegg.ratelimiter.config;

import com.chegg.ratelimiter.interceptor.RateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class RateLimitConfig implements WebMvcConfigurer {

    private final RateLimitProperties properties;
    private final RateLimitInterceptor interceptor;

    public RateLimitConfig(RateLimitProperties properties, RateLimitInterceptor interceptor) {
        this.properties = properties;
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        var registration = registry.addInterceptor(interceptor);
        properties.endpoints()
                .values()
                .stream()
                .map(RateLimitProperties.EndpointRule::path)
                .distinct()
                .forEach(registration::addPathPatterns);
    }
}
