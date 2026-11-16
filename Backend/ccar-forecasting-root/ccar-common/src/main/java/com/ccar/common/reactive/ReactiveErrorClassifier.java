package com.ccar.common.reactive;

import com.ccar.common.error.ErrorCode;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

/**
 * Maps whatever went wrong while handling or proxying a reactive request to the shared error catalogue.
 * <p>
 * Resilience4j and Netty types are matched by class name, so ccar-common does not depend on either.
 */
public final class ReactiveErrorClassifier {

    private static final String CALL_NOT_PERMITTED = "io.github.resilience4j.circuitbreaker.CallNotPermittedException";
    private static final String NETTY_TIMEOUT = "io.netty.handler.timeout.TimeoutException";

    private ReactiveErrorClassifier() {
    }

    public static ErrorCode classify(Throwable error) {
        int depth = 0;
        for (Throwable t = error; t != null && depth < 10; t = t.getCause(), depth++) {
            ErrorCode code = classifyOne(t);
            if (code != null) {
                return code;
            }
        }
        return ErrorCode.INTERNAL_ERROR;
    }

    private static ErrorCode classifyOne(Throwable t) {
        if (isA(t, CALL_NOT_PERMITTED)) {
            return ErrorCode.CIRCUIT_OPEN;
        }
        // response-timeout (TimeoutException / RSE 504), netty ReadTimeoutException, resilience4j time limiter
        if (t instanceof TimeoutException || t instanceof SocketTimeoutException || isA(t, NETTY_TIMEOUT)) {
            return ErrorCode.DOWNSTREAM_TIMEOUT;
        }
        // connection refused / connect timeout (netty ConnectTimeoutException extends ConnectException)
        if (t instanceof ConnectException || t instanceof UnknownHostException) {
            return ErrorCode.DOWNSTREAM_UNAVAILABLE;
        }
        if (t instanceof ResponseStatusException rse) {
            int status = rse.getStatusCode().value();
            return switch (status) {
                case 404 -> ErrorCode.ROUTE_NOT_FOUND;    // no route / handler for the path
                case 502, 503 -> ErrorCode.DOWNSTREAM_UNAVAILABLE; // incl. gateway NotFoundException (no instance)
                case 504 -> ErrorCode.DOWNSTREAM_TIMEOUT;
                default -> ErrorCode.fromStatus(status);
            };
        }
        // connection reset / premature close by the downstream service
        if (t instanceof IOException) {
            return ErrorCode.DOWNSTREAM_UNAVAILABLE;
        }
        return null;
    }

    /** Client-facing detail for a gateway-generated error; never contains host names or exception text. */
    public static String detail(ErrorCode code) {
        return switch (code) {
            case DOWNSTREAM_UNAVAILABLE -> "The service handling this request is unavailable. Please retry shortly.";
            case DOWNSTREAM_TIMEOUT -> "The service handling this request did not respond in time.";
            case CIRCUIT_OPEN -> "The service handling this request is temporarily unavailable after repeated "
                    + "failures. Please retry shortly.";
            case ROUTE_NOT_FOUND -> "No API route matches this request.";
            case INTERNAL_ERROR -> "An unexpected error occurred. Quote the correlationId when reporting this issue.";
            default -> code.title();
        };
    }

    /** {@code instanceof} by class name, walking the superclass chain. */
    private static boolean isA(Throwable t, String className) {
        for (Class<?> c = t.getClass(); c != null; c = c.getSuperclass()) {
            if (c.getName().equals(className)) {
                return true;
            }
        }
        return false;
    }
}
