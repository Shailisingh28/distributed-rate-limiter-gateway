# Distributed Rate Limiter + API Gateway

A rate-limiting API gateway that enforces limits consistently across multiple
gateway instances, using Redis as shared state with Lua scripts for atomicity.

## Why this project

Rate limiting looks trivial on a single machine (a counter in memory) and becomes
genuinely hard the moment you have more than one instance of your service running
behind a load balancer — which is every production system at scale. This project
implements the naive version, shows why it breaks, and then fixes it properly.

## Architecture

┌────────────┐
client ───▶ │ nginx │ (load balancer, round-robin, port 8080)
└─────┬──────┘
┌────────────┼────────────┐
▼ ▼ ▼
┌──────────┐ ┌──────────┐ ┌──────────┐
│gateway-1 │ │gateway-2 │ │gateway-3 │ (independent Spring Boot processes)
└────┬─────┘ └────┬─────┘ └────┬─────┘
└────────────┼────────────┘
▼
┌────────────┐
│ Redis │ (shared token bucket state, Lua script = atomic)
└────────────┘

## Algorithms implemented

| Algorithm                  | Where                           | Why                                                                                                                        |
| -------------------------- | ------------------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| **Token Bucket**           | In-memory + Redis (distributed) | Allows short bursts up to `capacity`, smooths to a steady rate over time — matches real traffic better than a hard cutoff. |
| **Sliding Window Counter** | In-memory                       | Reduces the boundary-burst problem of a naive fixed window, at lower memory cost than a full sliding log.                  |

## The core distributed-systems problem this solves

With naive in-memory rate limiting, each gateway instance keeps its own counter.
A client bouncing across instances behind a load balancer can get
`capacity × number_of_instances` requests through — completely defeating the
limit. Moving the counter to Redis fixes visibility, but introduces a new
race condition: two instances reading the same "1 token left" state and both
allowing a request. `token_bucket.lua` fixes this by running the entire
read-check-write sequence as a single atomic operation on the Redis server.

## Resilience

- **Circuit breaker** (Resilience4j) around the downstream call — opens after
  50% of the last 10 calls fail, so a struggling downstream doesn't pile up
  latency on the gateway.
- **Retry with backoff** for transient downstream failures.
- **Fail-open on Redis errors** — if Redis is unreachable, the limiter allows
  traffic through rather than taking the whole gateway down. Deliberate
  availability-over-strict-enforcement tradeoff, verified by stopping Redis
  mid-flight and confirming the gateway stayed up.

## Metrics

Prometheus + Actuator expose JVM stats, HTTP request metrics, and a custom
`gateway_ratelimit_rejected_total` counter tracking rejections per instance.

## Testing

- **Unit tests**: JUnit 5, covering both rate limiting algorithms (token bucket, sliding window) — `src/test/java`
- **Load testing**: Gatling, results below and in `docs/load-test-results/`
- **Manual distributed verification**: confirmed via curl bursts across the 3-instance Docker Compose setup, cross-checked against Redis state directly

## Load Test Results

Ran using Gatling, targeting nginx (port 8080) so load spreads across all 3
distributed gateway instances.

**Test profile:** ramp from 1 → 20 req/sec over 30s, then hold steady at 20
req/sec for 60s. Total duration: 90 seconds.

| Metric               | Value         |
| -------------------- | ------------- |
| Total requests       | 1,515         |
| Failed requests (KO) | 0             |
| Mean throughput      | 16.83 req/sec |
| Min latency          | 2ms           |
| p50 latency          | 9ms           |
| p75 latency          | 10ms          |
| p95 latency          | 164ms         |
| p99 latency          | 177ms         |
| Max latency          | 566ms         |

### Why p50 and p99 differ so much

The gap between p50 (9ms) and p99 (177ms) reflects two different code paths:

- **Rejected requests (429)** short-circuit at the rate limiter — no downstream
  call is made, so these return in single-digit milliseconds.
- **Allowed requests** proceed to call the downstream API over the real
  internet, which has its own variable network latency.

The rate limiter itself adds negligible overhead (~9ms). Most of the latency
variance comes from the downstream dependency, not the limiting logic.

### What I'd do differently at 10x scale

1. **Redis becomes a single point of contention** at high throughput, since
   every request requires a round-trip for the Lua script. I'd look at Redis
   Cluster with key-based sharding — the `{key}` hash-tag pattern was chosen
   specifically to make this migration straightforward.
2. **The downstream dependency's own capacity** would matter more — the
   circuit breaker would trip more often, which is correct behavior, but
   worth watching via the Prometheus metrics.
3. **nginx's default round-robin** is fine here, but `least_conn` would be
   worth considering at much higher load.

## Running locally

```bash
docker compose up --build
```

Starts: Redis, 3 gateway instances, nginx load balancer (port 8080).

Test it:

```bash
for i in {1..6}; do curl -s -o /dev/null -w "%{http_code}\n" \
  -H "X-Api-Key: test-user" http://localhost:8080/api/test; done
```

## Tech stack

Java 21, Spring Boot 4 (WebFlux), Redis (Lettuce reactive client), Resilience4j,
Docker Compose, nginx, Prometheus/Actuator, Gatling for load testing.
