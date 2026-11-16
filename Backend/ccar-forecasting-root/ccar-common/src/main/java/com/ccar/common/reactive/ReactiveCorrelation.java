package com.ccar.common.reactive;

import com.ccar.common.tracing.CorrelationId;
import org.springframework.web.server.ServerWebExchange;

/**
 * Exchange attributes shared by the reactive error handling and the gateway's correlation-id web filter
 * (WebFlux has no per-request MDC, so the id travels as an exchange attribute).
 */
public final class ReactiveCorrelation {

    /** The path the client called, kept for error bodies written after an internal forward (e.g. /fallback/..). */
    public static final String ORIGINAL_PATH_ATTRIBUTE = ReactiveCorrelation.class.getName() + ".originalPath";

    private ReactiveCorrelation() {
    }

    /** The correlation id assigned to this exchange (generates and stores one if no filter has run, e.g. in tests). */
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
