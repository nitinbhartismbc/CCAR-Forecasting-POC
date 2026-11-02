package com.iris.kdb.exception;

public class KdbQueryException extends KdbException {

    public KdbQueryException() {
        super();
    }

    public KdbQueryException(String message) {
        super(message);
    }

    public KdbQueryException(String message, Throwable cause) {
        super(message, cause);
    }

    public KdbQueryException(Throwable cause) {
        super(cause);
    }
}