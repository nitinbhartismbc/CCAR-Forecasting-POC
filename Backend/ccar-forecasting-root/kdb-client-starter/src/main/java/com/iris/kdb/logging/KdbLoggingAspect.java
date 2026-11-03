package com.iris.kdb.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs every public {@code KdbTemplate} call: duration, slow queries (WARN) and failures (ERROR).
 * Query text is only logged at DEBUG (or on slow/failed calls) and is truncated.
 */
@Aspect
public class KdbLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(KdbLoggingAspect.class);

    private final long slowQueryThresholdMs;
    private final int maxQueryLength;

    public KdbLoggingAspect(long slowQueryThresholdMs, int maxQueryLength) {
        this.slowQueryThresholdMs = slowQueryThresholdMs;
        this.maxQueryLength = maxQueryLength;
    }

    @Around("execution(public * com.iris.kdb.service.KdbTemplate.*(..))")
    public Object logKdbCall(ProceedingJoinPoint pjp) throws Throwable {

        String method = pjp.getSignature().getName();
        String query = extractQuery(pjp.getArgs());

        if (log.isDebugEnabled()) {
            log.debug("KDB {}() start query=[{}]", method, query);
        }

        long start = System.nanoTime();
        try {
            Object result = pjp.proceed();
            long ms = (System.nanoTime() - start) / 1_000_000;

            if (ms >= slowQueryThresholdMs) {
                log.warn("KDB {}() SLOW: {} ms (threshold {} ms) query=[{}]",
                        method, ms, slowQueryThresholdMs, query);
            } else {
                log.info("KDB {}() completed in {} ms", method, ms);
            }
            return result;

        } catch (Throwable ex) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            log.error("KDB {}() failed after {} ms query=[{}]: {}",
                    method, ms, query, ex.toString());
            throw ex;
        }
    }

    private String extractQuery(Object[] args) {
        if (args.length == 0 || !(args[0] instanceof String q)) {
            return "-";
        }
        return q.length() > maxQueryLength
                ? q.substring(0, maxQueryLength) + "...(truncated)"
                : q;
    }
}
