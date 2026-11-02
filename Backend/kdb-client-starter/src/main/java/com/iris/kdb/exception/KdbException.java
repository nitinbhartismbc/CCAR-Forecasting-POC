package com.iris.kdb.exception;

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
