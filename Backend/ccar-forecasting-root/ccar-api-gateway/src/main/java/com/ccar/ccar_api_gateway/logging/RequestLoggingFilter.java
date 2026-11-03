package com.ccar.ccar_api_gateway.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Gateway-wide request logging (reactive equivalent of an AOP logging aspect).
 * Assigns/propagates X-Correlation-Id to downstream services and logs method, path,
 * route, status and total latency for every proxied request.
 */
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    public static final String HEADER = "X-Correlation-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        ServerHttpRequest original = exchange.getRequest();
        String incoming = original.getHeaders().getFirst(HEADER);
        String correlationId = (incoming == null || incoming.isBlank())
                ? UUID.randomUUID().toString()
                : incoming;

        ServerHttpRequest request = original.mutate()
                .headers(h -> h.set(HEADER, correlationId))
                .build();
        ServerWebExchange mutated = exchange.mutate().request(request).build();
        // add on the way out only if the downstream service didn't already echo it (avoids a duplicate header)
        mutated.getResponse().beforeCommit(() -> {
            if (!mutated.getResponse().getHeaders().containsKey(HEADER)) {
                mutated.getResponse().getHeaders().set(HEADER, correlationId);
            }
            return Mono.empty();
        });

        String method = String.valueOf(request.getMethod());
        String path = request.getURI().getRawPath();
        long start = System.nanoTime();

        log.info("[{}] -> {} {}", correlationId, method, path);

        return chain.filter(mutated)
                .doOnError(ex -> log.error("[{}] x {} {} failed: {}",
                        correlationId, method, path, ex.toString()))
                .doFinally(signal -> {
                    long ms = (System.nanoTime() - start) / 1_000_000;
                    Route route = mutated.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
                    log.info("[{}] <- {} {} route={} status={} {} ms",
                            correlationId, method, path,
                            route != null ? route.getId() : "-",
                            mutated.getResponse().getStatusCode(), ms);
                });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
