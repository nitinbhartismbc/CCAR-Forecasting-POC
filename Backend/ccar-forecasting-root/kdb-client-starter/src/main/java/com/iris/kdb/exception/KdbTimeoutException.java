package com.iris.kdb.exception;

/**
 * The KDB process did not answer within {@code kdb.query-timeout-millis}. The connection is discarded,
 * because a late reply would otherwise be read as the answer to the next query on it.
 */
public class KdbTimeoutException extends KdbException {

    public KdbTimeoutException(String message) {
        super(message);
    }

    public KdbTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
