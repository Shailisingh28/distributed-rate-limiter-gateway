package com.shaili.ratelimiter.limiter;

import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import reactor.core.publisher.Mono;

public class RedisTokenBucketLimiter implements RateLimiter {
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final RedisScript<Long> script;
    private final long capacity;
    private final double refillTokensPerSecond;

    public RedisTokenBucketLimiter(ReactiveRedisTemplate<String, String> redisTemplate, long capacity,
            double refillTokensPerSecond) {
        this.redisTemplate = redisTemplate;
        this.capacity = capacity;
        this.refillTokensPerSecond = refillTokensPerSecond;
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setLocation(new ClassPathResource("token_bucket.lua"));
        redisScript.setResultType(Long.class);
        this.script = redisScript;
    }

    @Override
    public Mono<Boolean> tryAcquire(String key) {
        String bucketKey = "ratelimit:{" + key + "}";
        long now = System.currentTimeMillis();
        List<String> keys = List.of(bucketKey);
        List<String> args = List.of(String.valueOf(capacity), String.valueOf(refillTokensPerSecond),
                String.valueOf(now));
        return redisTemplate.execute(script, keys, args).next().map(result -> result == 1L).onErrorReturn(true);
        // fail-open: if Redis is unreachable, allow the request through
    }

}
