package com.shaili.ratelimiter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.shaili.ratelimiter.limiter.InMemoryTokenBucketLimiter;

public class InMemoryTokenBucketLimiterTest {
    @Test
    void allowsExactlyCapacityRequestsThenRejects() {
        InMemoryTokenBucketLimiter limiter = new InMemoryTokenBucketLimiter(5, 0.001);
        for (int i = 0; i < 5; i++) {
            boolean allowed = limiter.tryAcquire("User1").block();
            assertTrue(allowed, "Request " + (i + 1) + " should be allowed (within capacity)");
        }
        boolean sixthRequest = limiter.tryAcquire("User1").block();
        assertFalse(sixthRequest, "6th request should be rejected - bucket should be empty");
    }

    @Test
    void differentKeysHaveIndependentBuckets() {
        InMemoryTokenBucketLimiter limiter = new InMemoryTokenBucketLimiter(1, 0.001);
        // user-A gets their 1 token, then is exhausted
        assertTrue(limiter.tryAcquire("User1").block(), "user-A's first request should succeed");
        assertFalse(limiter.tryAcquire("User1").block(), "user-A's second request should be rejected");
        // user-B must be completely unaffected by user-A's usage
        assertTrue(limiter.tryAcquire("user-B").block(),
                "user-B should have their own independent bucket, unaffected by user-A");
    }

    @Test
    void refillsTokensOverTime() throws InterruptedException {
        InMemoryTokenBucketLimiter limiter = new InMemoryTokenBucketLimiter(1, 10.0);
        assertTrue(limiter.tryAcquire("user-1").block(), "First request should consume the only token");
        assertFalse(limiter.tryAcquire("user-1").block(), "Second immediate request should be rejected");
        Thread.sleep(150);
        assertTrue(limiter.tryAcquire("user-1").block(),
                "After waiting for refill, a request should be allowed again");

    }
}
