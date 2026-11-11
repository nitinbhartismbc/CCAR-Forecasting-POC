package com.forecast.ccar.exception;

import com.ccar.common.error.ErrorCode;
import com.ccar.common.web.CcarExceptionHandler;
import com.iris.kdb.exception.KdbConnectionException;
import com.iris.kdb.exception.KdbException;
import com.iris.kdb.exception.KdbPoolExhaustedException;
import com.iris.kdb.exception.KdbQueryException;
import com.iris.kdb.exception.KdbTimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

/**
 * ccar-core error mapping. Generic, framework and {@code CcarException} handling is inherited from
 * {@link CcarExceptionHandler}; this class adds the KDB data-store failures.
 * <p>
 * KDB exception messages (host, q error text) are logged, never returned: the client gets a generic detail
 * plus the correlation id to quote.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends CcarExceptionHandler {

    private final long retryAfterSeconds;

    public GlobalExceptionHandler(@Value("${ccar.errors.retry-after-seconds:30}") long retryAfterSeconds) {
        this.retryAfterSeconds = retryAfterSeconds;
    }

    @ExceptionHandler(KdbException.class)
    public ResponseEntity<Object> handleKdb(KdbException ex, WebRequest request) {
        ErrorCode code = classify(ex);
        HttpHeaders headers = new HttpHeaders();
        if (code.status() == 503) {
            headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        }
        return respond(ex, code, problem(code, detail(code), request), headers);
    }

    static ErrorCode classify(KdbException ex) {
        if (ex instanceof KdbPoolExhaustedException) {
            return ErrorCode.KDB_POOL_EXHAUSTED;
        }
        if (ex instanceof KdbConnectionException) {
            return ErrorCode.KDB_UNAVAILABLE;
        }
        if (ex instanceof KdbTimeoutException) {
            return ErrorCode.KDB_TIMEOUT;
        }
        if (ex instanceof KdbQueryException) {
            return ErrorCode.KDB_QUERY_FAILED;
        }
        return ErrorCode.KDB_UNAVAILABLE;
    }

    private static String detail(ErrorCode code) {
        return switch (code) {
            case KDB_POOL_EXHAUSTED -> "The forecasting data store is busy. Please retry shortly.";
            case KDB_TIMEOUT -> "The forecasting data store did not respond in time.";
            case KDB_QUERY_FAILED -> "The forecasting data store could not process the request.";
            default -> "The forecasting data store is currently unavailable. Please retry shortly.";
        };
    }
}
