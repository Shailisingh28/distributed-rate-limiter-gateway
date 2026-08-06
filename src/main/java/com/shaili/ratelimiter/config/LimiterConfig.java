package com.shaili.ratelimiter.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.shaili.ratelimiter.limiter.RateLimiter;
import com.shaili.ratelimiter.limiter.RedisTokenBucketLimiter;

@Configuration
public class LimiterConfig {

    @Bean
    public RateLimiter rateLimiter(
            @Qualifier("reactiveRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate) {
        return new RedisTokenBucketLimiter(redisTemplate, 5, 1.0);
    }

    @Bean
    public ReactiveRedisTemplate<String, String> reactiveRedisTemplate(
            ReactiveRedisConnectionFactory factory) {
        RedisSerializationContext<String, String> context = RedisSerializationContext
                .<String, String>newSerializationContext(new StringRedisSerializer())
                .value(StringRedisSerializer.UTF_8)
                .build();
        return new ReactiveRedisTemplate<>(factory, context);
    }
}