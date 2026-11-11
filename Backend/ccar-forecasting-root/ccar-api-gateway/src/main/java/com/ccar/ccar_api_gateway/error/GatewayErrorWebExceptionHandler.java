package com.ccar.ccar_api_gateway.error;

import com.ccar.ccar_api_gateway.logging.CorrelationIdWebFilter;
import com.ccar.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global error handler of the gateway (runs before Boot's default handler, order -1). Turns every unhandled
 * error - downstream refused/timed out, no route, unexpected failure - into the shared problem format with the
 * correlation id. Errors returned by a downstream service are NOT touched: they already use the format and are
 * passed through as-is.
 */
@Component
@Order(-2)
public class GatewayErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayErrorWebExceptionHandler.class);

    private final ProblemResponseWriter writer;

    public GatewayErrorWebExceptionHandler(ProblemResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }

        ErrorCode code = GatewayErrorClassifier.classify(ex);
        String correlationId = CorrelationIdWebFilter.correlationId(exchange);
        String path = exchange.getRequest().getPath().value();

        if (code == ErrorCode.INTERNAL_ERROR) {
            log.error("[{}] {} failed: {}", correlationId, path, ex.toString(), ex);
        } else if (code.isServerError()) {
            // expected operational failure (service down / slow): one line, no stack trace
            log.warn("[{}] {} failed: {} ({})", correlationId, path, code, ex.toString());
        } else {
            log.info("[{}] {} rejected: {}", correlationId, path, code);
        }

        return writer.write(exchange, code, GatewayErrorClassifier.detail(code));
    }
}
