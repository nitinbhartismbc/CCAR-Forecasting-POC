package com.ccar.common.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * Logs entry/exit and duration for the controller and service beans of every CCAR service
 * (failures only at DEBUG - each service's global exception handler logs each failure exactly once).
 * Arguments and results are only logged at DEBUG and are truncated.
 * <p>
 * Works for both stacks: on the servlet services the duration is measured around the call; when a method returns
 * a Reactor {@code Mono}/{@code Flux} (api-gateway) it is measured until the publisher terminates.
 * <p>
 * Not a {@code @Component}: registered by {@link CcarLoggingAutoConfiguration}. KDB calls via KdbTemplate are
 * logged separately by the kdb-starter's own aspect.
 */
@Aspect
public class LoggingAspect {

    public static final int DEFAULT_MAX_LOG_LENGTH = 500;

    private static final boolean REACTOR_PRESENT = isPresent("reactor.core.publisher.Mono");

    private final int maxLogLength;

    public LoggingAspect() {
        this(DEFAULT_MAX_LOG_LENGTH);
    }

    public LoggingAspect(int maxLogLength) {
        this.maxLogLength = maxLogLength > 0 ? maxLogLength : DEFAULT_MAX_LOG_LENGTH;
    }

    /** CCAR application code only - never framework/library beans, never ccar-common itself. */
    @Pointcut("(within(com.ccar..*) || within(com.forecast..*)) && !within(com.ccar.common..*)")
    public void applicationCode() {}

    // @within matches the declared annotation only (not meta-annotations), so each stereotype is listed.
    @Pointcut("@within(org.springframework.web.bind.annotation.RestController)"
            + " || @within(org.springframework.stereotype.Controller)")
    public void controllerLayer() {}

    @Pointcut("@within(org.springframework.stereotype.Service)")
    public void serviceLayer() {}

    @Around("applicationCode() && (controllerLayer() || serviceLayer())")
    public Object logAround(ProceedingJoinPoint pjp) throws Throwable {

        Logger log = LoggerFactory.getLogger(pjp.getSignature().getDeclaringType());
        String method = pjp.getSignature().getName();

        if (log.isDebugEnabled()) {
            log.debug("-> {}() args={}", method, truncate(Arrays.toString(pjp.getArgs())));
        }

        long start = System.nanoTime();
        try {
            Object result = pjp.proceed();
            if (REACTOR_PRESENT && ReactiveLogging.isReactive(result)) {
                // Completion is logged when the publisher terminates, not when the method returns.
                return ReactiveLogging.decorate(result, this, log, method, start);
            }
            logCompleted(log, method, start, result);
            return result;

        } catch (Throwable ex) {
            logFailed(log, method, start, ex);
            throw ex;
        }
    }

    void logCompleted(Logger log, String method, long startNanos, Object result) {
        log.info("<- {}() completed in {} ms", method, elapsedMs(startNanos));
        if (log.isDebugEnabled()) {
            log.debug("<- {}() result={}", method, truncate(String.valueOf(result)));
        }
    }

    void logFailed(Logger log, String method, long startNanos, Throwable ex) {
        // DEBUG only: the failure is logged once (ERROR + stack for 5xx) by the service's exception handler.
        log.debug("x {}() failed after {} ms: {}", method, elapsedMs(startNanos), ex.toString());
    }

    String truncate(String s) {
        return s.length() > maxLogLength ? s.substring(0, maxLogLength) + "...(truncated)" : s;
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static boolean isPresent(String className) {
        try {
            Class.forName(className, false, LoggingAspect.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
