package com.shaili.ratelimiter.limiter;

import java.util.concurrent.ConcurrentHashMap;

import reactor.core.publisher.Mono;

public class InMemorySlidingWindowLimiter implements RateLimiter {
    private final long maxRequestsPerWindow;
    private final long windowSizeMillis;
    private final ConcurrentHashMap<String, WindowState> states = new ConcurrentHashMap<>();

    public InMemorySlidingWindowLimiter(long maxRequestsPerWindow, long windowSizeSeconds) {
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.windowSizeMillis = windowSizeSeconds * 1000;
    }

    @Override
    public Mono<Boolean> tryAcquire(String key) {
        WindowState state = states.computeIfAbsent(key, k -> new WindowState());
        boolean allowed = state.tryAcquire(maxRequestsPerWindow, windowSizeMillis);
        return Mono.just(allowed);
    }

    public static class WindowState {
        private long currentWindowStart;
        private long currentWindowCount;
        private long previousWindowCount;

        synchronized boolean tryAcquire(long maxRequests, long windowSizeMillis) {
            long now = System.currentTimeMillis();
            if (currentWindowStart == 0) {
                currentWindowStart = now;
            }
            long elapsedSinceWindowStart = now - currentWindowStart;
            if (elapsedSinceWindowStart >= windowSizeMillis) {
                long windowsElapsed = elapsedSinceWindowStart / windowSizeMillis;
                if (windowSizeMillis == 1) {
                    // exactly one window boundary crossed - shift current into previous
                    previousWindowCount = currentWindowCount;
                } else {
                    // more than one window has passed with no activity in between -
                    // there's nothing recent to "remember," so previous window is empty
                    previousWindowCount = 0;
                }
                currentWindowCount = 0;
                currentWindowStart += windowsElapsed * windowSizeMillis;
                elapsedSinceWindowStart = now - currentWindowStart;
            }
            double weightOfPreviousWindow = (double) (windowSizeMillis - elapsedSinceWindowStart) / windowSizeMillis;
            double estimatedCount = previousWindowCount * weightOfPreviousWindow + currentWindowCount;
            if (estimatedCount < maxRequests) {
                currentWindowCount++;
                return true;
            }
            return false;
        }
    }
}
