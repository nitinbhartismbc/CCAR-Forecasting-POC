package com.ccar.ccar_api_gateway.error;

import com.ccar.ccar_api_gateway.logging.CorrelationIdWebFilter;
import com.ccar.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Target of the routes' {@code CircuitBreaker} filter ({@code fallbackUri: forward:/fallback/<service>}).
 * Reached when the downstream call failed (refused, timed out) or the circuit is open, and answers with the
 * shared problem format: 503 DOWNSTREAM_UNAVAILABLE / CIRCUIT_OPEN or 504 DOWNSTREAM_TIMEOUT.
 */
@RestController
public class FallbackController {

    private static final Logger log = LoggerFactory.getLogger(FallbackController.class);

    private final ProblemResponseWriter writer;

    public FallbackController(ProblemResponseWriter writer) {
        this.writer = writer;
    }

    @RequestMapping("/fallback/{service}")
    public Mono<Void> fallback(@PathVariable String service, ServerWebExchange exchange) {
        Throwable cause = exchange.getAttribute(ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR);
        ErrorCode code = cause == null ? ErrorCode.DOWNSTREAM_UNAVAILABLE : GatewayErrorClassifier.classify(cause);
        if (!code.isServerError()) {
            code = ErrorCode.DOWNSTREAM_UNAVAILABLE;
        }

        log.warn("[{}] call to {} failed, circuit-breaker fallback: {} ({})",
                CorrelationIdWebFilter.correlationId(exchange), service, code,
                cause == null ? "no exception" : cause.toString());

        // instance = the client's path, not the internal /fallback/.. forward (see ProblemResponseWriter)
        return writer.write(exchange, code, GatewayErrorClassifier.detail(code));
    }
}
