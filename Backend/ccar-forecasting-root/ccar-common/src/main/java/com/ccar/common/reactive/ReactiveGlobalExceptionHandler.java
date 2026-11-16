package com.ccar.common.reactive;

import com.ccar.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

/**
 * Global exception handler of the reactive api-gateway - the WebFlux counterpart of the servlet
 * {@code com.ccar.common.web.GlobalExceptionHandler}. Runs before Boot's default error handler (order -2 vs -1)
 * and turns every unhandled error - downstream refused/timed out, circuit open, no route, unexpected failure -
 * into the shared problem format with the correlation id. Errors returned by a downstream service are NOT
 * touched: they already use the format and are passed through as-is.
 * <p>
 * Logging happens here only: unexpected errors at ERROR with stack, operational 5xx as one WARN line,
 * 4xx at INFO. Registered by {@link CcarReactiveAutoConfiguration}.
 */
public class ReactiveGlobalExceptionHandler implements WebExceptionHandler, Ordered {

    public static final int ORDER = -2;

    private static final Logger log = LoggerFactory.getLogger(ReactiveGlobalExceptionHandler.class);

    private final ProblemResponseWriter writer;

    public ReactiveGlobalExceptionHandler(ProblemResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }

        ErrorCode code = ReactiveErrorClassifier.classify(ex);
        String correlationId = ReactiveCorrelation.correlationId(exchange);
        String path = exchange.getRequest().getPath().value();

        if (code == ErrorCode.INTERNAL_ERROR) {
            log.error("[{}] {} failed: {}", correlationId, path, ex.toString(), ex);
        } else if (code.isServerError()) {
            // expected operational failure (service down / slow): one line, no stack trace
            log.warn("[{}] {} failed: {} ({})", correlationId, path, code, ex.toString());
        } else {
            log.info("[{}] {} rejected: {}", correlationId, path, code);
        }

        return writer.write(exchange, code, ReactiveErrorClassifier.detail(code));
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
