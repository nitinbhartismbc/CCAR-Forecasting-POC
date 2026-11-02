package com.iris.kdb.exception;

public class KdbPoolExhaustedException extends KdbException {

    public KdbPoolExhaustedException() {
        super();
    }

    public KdbPoolExhaustedException(String message) {
        super(message);
    }

    public KdbPoolExhaustedException(String message, Throwable cause) {
        super(message, cause);
    }

    public KdbPoolExhaustedException(Throwable cause) {
        super(cause);
    }
}