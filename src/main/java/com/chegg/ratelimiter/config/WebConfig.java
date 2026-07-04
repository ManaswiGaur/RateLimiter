package com.chegg.ratelimiter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class WebConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
