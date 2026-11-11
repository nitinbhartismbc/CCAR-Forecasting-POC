package com.ccar.ccar_api_gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Defaults for every route circuit breaker (one per downstream service, see the routes' CircuitBreaker filter).
 * <p>
 * Only transport failures (refused, timed out) count as failures; a 4xx/5xx response from a service is a valid
 * answer and is passed through. The time limiter must be longer than the longest route response-timeout plus
 * retries, otherwise it would cut off requests the HTTP client is still allowed to wait for.
 */
@Configuration
public class ResilienceConfig {

    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> defaultCircuitBreakerConfig(
            @Value("${ccar.gateway.circuit-breaker.failure-rate-threshold:50}") float failureRateThreshold,
            @Value("${ccar.gateway.circuit-breaker.sliding-window-size:20}") int slidingWindowSize,
            @Value("${ccar.gateway.circuit-breaker.minimum-number-of-calls:10}") int minimumNumberOfCalls,
            @Value("${ccar.gateway.circuit-breaker.wait-in-open-state:30s}") Duration waitInOpenState,
            @Value("${ccar.gateway.circuit-breaker.time-limit:40s}") Duration timeLimit) {

        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                        .slidingWindowSize(slidingWindowSize)
                        .minimumNumberOfCalls(minimumNumberOfCalls)
                        .failureRateThreshold(failureRateThreshold)
                        .waitDurationInOpenState(waitInOpenState)
                        .permittedNumberOfCallsInHalfOpenState(3)
                        .automaticTransitionFromOpenToHalfOpenEnabled(true)
                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(timeLimit).build())
                .build());
    }
}
