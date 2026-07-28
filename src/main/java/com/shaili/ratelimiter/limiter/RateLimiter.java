package com.shaili.ratelimiter.limiter;

import reactor.core.publisher.Mono;

public interface RateLimiter {
    Mono<Boolean> tryAcquire(String key);

}
