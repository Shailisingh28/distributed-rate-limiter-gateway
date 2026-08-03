package com.shaili.ratelimiter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.shaili.ratelimiter.limiter.InMemoryTokenBucketLimiter;
import com.shaili.ratelimiter.limiter.RateLimiter;

@Configuration
public class LimiterConfig {
    @Bean
    public RateLimiter rateLimiter() {
        return new InMemoryTokenBucketLimiter(5, 1.0);
    }
}
