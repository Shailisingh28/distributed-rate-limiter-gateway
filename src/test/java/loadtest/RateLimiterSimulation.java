package loadtest;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class RateLimiterSimulation extends Simulation {

    // Point this at nginx (port 8080), so load spreads across all 3 gateway
    // instances -
    // this is what actually tests the DISTRIBUTED behavior under real concurrent
    // load,
    // not just a single instance.
    HttpProtocolBuilder httpProtocol = http
            .baseUrl("http://localhost:8080")
            .header("X-Api-Key", "load-test-user")
            .acceptHeader("application/json");

    ScenarioBuilder scn = scenario("Sustained load against distributed gateway")
            .exec(
                    http("get_test_endpoint")
                            .get("/api/test")
                            .check(status().in(200, 429, 503)));

    {
        setUp(
                scn.injectOpen(
                        // Phase 1: ramp up gradually from 1 to 20 users/sec over 30 seconds
                        rampUsersPerSec(1).to(20).during(Duration.ofSeconds(30)),
                        // Phase 2: hold steady at 20 users/sec for 60 seconds - this is the sustained
                        // load phase
                        constantUsersPerSec(20).during(Duration.ofSeconds(60))))
                .protocols(httpProtocol);
    }
}