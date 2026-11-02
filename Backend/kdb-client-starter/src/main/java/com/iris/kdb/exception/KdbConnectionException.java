package com.iris.kdb.exception;

public class KdbConnectionException extends KdbException {

    public KdbConnectionException() {
        super();
    }

    public KdbConnectionException(String message) {
        super(message);
    }

    public KdbConnectionException(String message, Throwable cause) {
        super(message, cause);
    }

    public KdbConnectionException(Throwable cause) {
        super(cause);
    }
}
