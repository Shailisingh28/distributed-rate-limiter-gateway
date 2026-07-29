package com.shaili.ratelimiter.limiter;

import java.util.concurrent.ConcurrentHashMap;

import reactor.core.publisher.Mono;

public class InMemoryTokenBucketLimiter implements RateLimiter {
    private final long capacity;
    private final double refillTokensPerSecond;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();// thread safe

    public InMemoryTokenBucketLimiter(long capacity, double refillTokensPerSecond) {
        this.capacity = capacity;
        this.refillTokensPerSecond = refillTokensPerSecond;
    }

    @Override
    public Mono<Boolean> tryAcquire(String key) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(capacity));
        boolean allowed = bucket.tryConsume(capacity, refillTokensPerSecond);
        return Mono.just(allowed);
    }

    private static class Bucket {
        private double tokens;
        private long lastRefillTimestampMillis;

        Bucket(double initialTokens) {
            this.tokens = initialTokens;
            this.lastRefillTimestampMillis = System.currentTimeMillis();
        }

        synchronized boolean tryConsume(long capacity, double refillPerSecond) {
            refill(capacity, refillPerSecond);
            if (tokens >= 1) {
                tokens -= 1;
                return true;
            }
            return false;
        }

        private void refill(long capacity, double refillPerSecond) {
            long now = System.currentTimeMillis();
            double elapsedSeconds = (now - lastRefillTimestampMillis) / 1000.0;
            double tokensToAdd = elapsedSeconds * refillPerSecond;
            if (tokensToAdd > 0) {
                tokens = Math.min(tokensToAdd + tokens, capacity);
                lastRefillTimestampMillis = now;
            }
        }
    }
}
