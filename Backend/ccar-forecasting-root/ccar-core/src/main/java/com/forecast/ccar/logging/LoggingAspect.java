package com.forecast.ccar.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Logs entry/exit, duration and exceptions for controller and service beans.
 * Arguments and results are only logged at DEBUG and are truncated.
 * KDB calls via KdbTemplate are logged separately by the kdb-starter's own aspect.
 */
@Aspect
@Component
public class LoggingAspect {

    private static final int MAX_LOG_LENGTH = 500;

    @Pointcut("within(com.forecast.ccar.controller..*)")
    public void controllerLayer() {}

    @Pointcut("within(com.forecast.ccar.service..*)")
    public void serviceLayer() {}

    @Around("controllerLayer() || serviceLayer()")
    public Object logAround(ProceedingJoinPoint pjp) throws Throwable {

        Logger log = LoggerFactory.getLogger(pjp.getSignature().getDeclaringType());
        String method = pjp.getSignature().getName();

        if (log.isDebugEnabled()) {
            log.debug("-> {}() args={}", method, truncate(Arrays.toString(pjp.getArgs())));
        }

        long start = System.nanoTime();
        try {
            Object result = pjp.proceed();
            long ms = (System.nanoTime() - start) / 1_000_000;
            log.info("<- {}() completed in {} ms", method, ms);
            if (log.isDebugEnabled()) {
                log.debug("<- {}() result={}", method, truncate(String.valueOf(result)));
            }
            return result;

        } catch (Throwable ex) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            log.error("x {}() failed after {} ms: {}", method, ms, ex.toString());
            throw ex;
        }
    }

    private static String truncate(String s) {
        return s.length() > MAX_LOG_LENGTH ? s.substring(0, MAX_LOG_LENGTH) + "...(truncated)" : s;
    }
}
