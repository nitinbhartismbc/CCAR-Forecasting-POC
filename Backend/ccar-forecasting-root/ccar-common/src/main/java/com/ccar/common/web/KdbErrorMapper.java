package com.ccar.common.web;

import com.ccar.common.error.ErrorCode;
import com.iris.kdb.exception.KdbConnectionException;
import com.iris.kdb.exception.KdbException;
import com.iris.kdb.exception.KdbPoolExhaustedException;
import com.iris.kdb.exception.KdbQueryException;
import com.iris.kdb.exception.KdbTimeoutException;

/**
 * KDB data-store failures (kdb-starter), registered only when kdb-starter is on the classpath.
 * <p>
 * KDB exception messages (host, q error text) are logged by the handler, never returned: the client gets a
 * generic detail plus the correlation id to quote. 503s carry {@code Retry-After}.
 */
public class KdbErrorMapper implements CcarErrorMapper {

    private final long retryAfterSeconds;

    public KdbErrorMapper(long retryAfterSeconds) {
        this.retryAfterSeconds = retryAfterSeconds;
    }

    @Override
    public MappedError map(Throwable ex) {
        if (!(ex instanceof KdbException kdb)) {
            return null;
        }
        ErrorCode code = classify(kdb);
        return new MappedError(code, detail(code), code.status() == 503 ? retryAfterSeconds : null);
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
