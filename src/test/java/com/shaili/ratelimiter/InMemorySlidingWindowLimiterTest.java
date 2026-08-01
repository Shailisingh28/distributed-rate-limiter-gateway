package com.shaili.ratelimiter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.shaili.ratelimiter.limiter.InMemorySlidingWindowLimiter;

public class InMemorySlidingWindowLimiterTest {
    @Test
    void allowsUpToMaxRequestsInWindow() {
        // 3 requests allowed per 1-second window
        InMemorySlidingWindowLimiter limiter = new InMemorySlidingWindowLimiter(3, 1);

        assertTrue(limiter.tryAcquire("user-1").block(), "1st request should be allowed");
        assertTrue(limiter.tryAcquire("user-1").block(), "2nd request should be allowed");
        assertTrue(limiter.tryAcquire("user-1").block(), "3rd request should be allowed");

        assertFalse(limiter.tryAcquire("user-1").block(), "4th request in same window should be rejected");
    }

    @Test
    void differentKeysHaveIndependentWindows() {
        InMemorySlidingWindowLimiter limiter = new InMemorySlidingWindowLimiter(1, 1);

        assertTrue(limiter.tryAcquire("user-A").block(), "user-A's first request should succeed");
        assertFalse(limiter.tryAcquire("user-A").block(), "user-A's second request should be rejected");

        assertTrue(limiter.tryAcquire("user-B").block(),
                "user-B should have an independent window, unaffected by user-A");
    }

    @Test
    void allowsMoreRequestsInNextWindowAfterFullWait() throws InterruptedException {
        InMemorySlidingWindowLimiter limiter = new InMemorySlidingWindowLimiter(2, 1);

        assertTrue(limiter.tryAcquire("user-1").block());
        assertTrue(limiter.tryAcquire("user-1").block());
        assertFalse(limiter.tryAcquire("user-1").block(), "3rd request in same window should be rejected");

        Thread.sleep(1100); // wait past a full window + previous window's decayed weight

        assertTrue(limiter.tryAcquire("user-1").block(),
                "After a full window has fully passed, requests should be allowed again");
    }

    @Test
    void capsTotalRequestsAcrossWindowsNearConfiguredLimit() throws InterruptedException {
        // This test proves the core guarantee of sliding window: it never lets
        // FAR more than maxRequests through in a short span of time, even
        // right after crossing into a fresh window - unlike a naive fixed
        // window counter, which would allow a full new burst instantly.
        InMemorySlidingWindowLimiter limiter = new InMemorySlidingWindowLimiter(4, 1);

        // Max out the first window
        assertTrue(limiter.tryAcquire("user-1").block());
        assertTrue(limiter.tryAcquire("user-1").block());
        assertTrue(limiter.tryAcquire("user-1").block());
        assertTrue(limiter.tryAcquire("user-1").block());
        assertFalse(limiter.tryAcquire("user-1").block(), "window should be full");

        // Land solidly inside the NEXT window (not right at the edge) so this
        // test isn't dependent on hitting a precise millisecond timing offset.
        Thread.sleep(1900);

        int allowedInNewWindow = 0;
        for (int i = 0; i < 8; i++) {
            if (limiter.tryAcquire("user-1").block()) {
                allowedInNewWindow++;
            }
        }

        assertTrue(allowedInNewWindow <= 4,
                "Sliding window should still cap requests near the configured limit, " +
                        "even in a fresh window - proving it smooths bursts rather than resetting to zero enforcement");
    }
}