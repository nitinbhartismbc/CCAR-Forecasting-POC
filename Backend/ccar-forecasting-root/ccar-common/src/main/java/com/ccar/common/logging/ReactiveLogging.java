package com.ccar.common.logging;

import org.slf4j.Logger;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

/**
 * Reactor-specific part of {@link LoggingAspect}, kept in its own class so the servlet services
 * (no Reactor on the classpath) never load it.
 */
final class ReactiveLogging {

    private ReactiveLogging() {
    }

    static boolean isReactive(Object result) {
        return result instanceof Mono || result instanceof Flux;
    }

    /** Times the publisher from the method call until it completes, errors or is cancelled. */
    static Object decorate(Object result, LoggingAspect aspect, Logger log, String method, long startNanos) {
        if (result instanceof Mono<?> mono) {
            return mono
                    .doOnSuccess(value -> aspect.logCompleted(log, method, startNanos, value))
                    .doOnError(ex -> aspect.logFailed(log, method, startNanos, ex))
                    .doOnCancel(() -> log.debug("x {}() cancelled", method));
        }
        Flux<?> flux = (Flux<?>) result;
        return flux
                .doOnError(ex -> aspect.logFailed(log, method, startNanos, ex))
                .doFinally(signal -> {
                    if (signal == SignalType.ON_COMPLETE) {
                        aspect.logCompleted(log, method, startNanos, "<flux>");
                    } else if (signal == SignalType.CANCEL) {
                        log.debug("x {}() cancelled", method);
                    }
                });
    }
}
