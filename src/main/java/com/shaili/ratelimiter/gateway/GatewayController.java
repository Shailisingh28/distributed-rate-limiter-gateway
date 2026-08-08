package com.shaili.ratelimiter.gateway;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import com.shaili.ratelimiter.limiter.RateLimiter;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import reactor.core.publisher.Mono;

@RestController
public class GatewayController {
    private final RateLimiter rateLimiter;
    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;

    public GatewayController(RateLimiter rateLimiter, WebClient.Builder webClientBuilder,
            CircuitBreakerRegistry circuitBreakerRegistry) {
        this.rateLimiter = rateLimiter;
        this.webClient = webClientBuilder.baseUrl("https://jsonplaceholder.typicode.com").build();
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("downstream-service");
    }

    @GetMapping("/api/test")
    public Mono<ResponseEntity<String>> handleRequest(
            @RequestHeader(value = "X-Api-Key", defaultValue = "anonymous") String apiKey) {
        return rateLimiter.tryAcquire(apiKey).flatMap(allowed -> {
            if (!allowed) {
                return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body("Rate limit exceeded for: " + apiKey));
            }
            return callDownstream();
        });
    }

    private Mono<ResponseEntity<String>> callDownstream() {
        return webClient.get()
                .uri("/todos/1")
                .retrieve()
                .bodyToMono(String.class)
                .map(ResponseEntity::ok)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .onErrorResume(ex -> Mono.just(
                        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                                .body("Downstream unavailable, circuit breaker engaged: " + ex.getMessage())));
        // take the value
        // inside the
        // mono transform
        // into another
        // value aur new
        // MONO return
        // kro tranformed
        // value k saath
    }
}
