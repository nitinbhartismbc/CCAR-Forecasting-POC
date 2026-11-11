package com.iris.kdb.exception;

/**
 * Root of all KDB client failures. Subclasses tell the caller what went wrong and so how to react:
 * <ul>
 *   <li>{@link KdbConnectionException} - KDB unreachable / login refused / connection broke. Retry later.</li>
 *   <li>{@link KdbPoolExhaustedException} - every pooled connection busy for {@code kdb.max-wait-millis}.
 *       Retry later.</li>
 *   <li>{@link KdbTimeoutException} - KDB did not answer within {@code kdb.query-timeout-millis}.</li>
 *   <li>{@link KdbQueryException} - KDB rejected the query (q error such as {@code 'type}) or returned an
 *       unexpected result type. The connection itself is healthy; retrying the same query will fail again.</li>
 * </ul>
 * Messages may contain host names and q error text: log them, but don't return them to API clients.
 */
public class KdbException extends RuntimeException {

    public KdbException() {
        super();
    }

    public KdbException(String message) {
        super(message);
    }

    public KdbException(String message, Throwable cause) {
        super(message, cause);
    }

    public KdbException(Throwable cause) {
        super(cause);
    }
}
