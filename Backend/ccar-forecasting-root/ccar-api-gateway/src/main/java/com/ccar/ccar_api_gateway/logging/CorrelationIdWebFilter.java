package com.ccar.ccar_api_gateway.logging;

import com.ccar.common.tracing.CorrelationId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Entry point of distributed tracing. Runs first for <b>every</b> request (a {@link WebFilter}, so also for
 * unrouted paths and fallback forwards, unlike a route-only GlobalFilter):
 * <ol>
 *   <li>takes the caller's {@code X-Correlation-Id} (the UI sends one) or generates one when it is missing or
 *       not log-safe;</li>
 *   <li>forwards it to the downstream service, which puts it in its MDC / logs / error bodies;</li>
 *   <li>stores it as an exchange attribute (read by the JWT filter, error handler and fallback) and in the
 *       Reactor context;</li>
 *   <li>echoes it on the response - success or error;</li>
 *   <li>logs one line in and one line out (with route, status, latency).</li>
 * </ol>
 */
@Component
public class CorrelationIdWebFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdWebFilter.class);

    public static final String ORIGINAL_PATH_ATTRIBUTE = CorrelationIdWebFilter.class.getName() + ".originalPath";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {

        String correlationId = CorrelationId.resolve(exchange.getRequest().getHeaders().getFirst(CorrelationId.HEADER));
        exchange.getAttributes().put(CorrelationId.ATTRIBUTE, correlationId);
        // the client's path, kept for error bodies written after an internal forward (e.g. /fallback/..)
        exchange.getAttributes().put(ORIGINAL_PATH_ATTRIBUTE, exchange.getRequest().getPath().value());

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(h -> h.set(CorrelationId.HEADER, correlationId))
                .build();
        ServerWebExchange mutated = exchange.mutate().request(request).build();

        String method = String.valueOf(request.getMethod());
        String path = request.getURI().getRawPath();
        long start = System.nanoTime();

        log.info("[{}] -> {} {}", correlationId, method, path);

        // On commit: (1) set the header exactly once - replacing a downstream echo instead of duplicating it -
        // on every response, including ones written by error handlers; (2) log the final status.
        mutated.getResponse().beforeCommit(() -> {
            mutated.getResponse().getHeaders().set(CorrelationId.HEADER, correlationId);
            long ms = (System.nanoTime() - start) / 1_000_000;
            Route route = mutated.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            log.info("[{}] <- {} {} route={} status={} {} ms",
                    correlationId, method, path,
                    route != null ? route.getId() : "-",
                    mutated.getResponse().getStatusCode(), ms);
            return Mono.empty();
        });

        return chain.filter(mutated)
                .contextWrite(Context.of(CorrelationId.MDC_KEY, correlationId));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    /** The correlation id assigned to this exchange (generates one if the filter has not run, e.g. in tests). */
    public static String correlationId(ServerWebExchange exchange) {
        Object attr = exchange.getAttribute(CorrelationId.ATTRIBUTE);
        if (attr != null) {
            return attr.toString();
        }
        String resolved = CorrelationId.resolve(exchange.getRequest().getHeaders().getFirst(CorrelationId.HEADER));
        exchange.getAttributes().put(CorrelationId.ATTRIBUTE, resolved);
        return resolved;
    }

    /** The path the client called, even after an internal forward. */
    public static String originalPath(ServerWebExchange exchange) {
        Object attr = exchange.getAttribute(ORIGINAL_PATH_ATTRIBUTE);
        return attr != null ? attr.toString() : exchange.getRequest().getPath().value();
    }
}
