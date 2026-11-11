package com.ccar.common.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Base class for every application exception that should reach a client as a specific {@link ErrorCode}.
 * <p>
 * {@link #getMessage()} is the client-safe {@code detail} of the problem response, so it must never carry
 * internals (queries, stack details, credentials). Put those in the {@code cause}; it is logged server-side
 * against the correlation id but never serialised.
 */
public class CcarException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Long retryAfterSeconds;
    private final Map<String, Object> properties = new LinkedHashMap<>();

    public CcarException(ErrorCode errorCode, String detail) {
        this(errorCode, detail, null, null);
    }

    public CcarException(ErrorCode errorCode, String detail, Throwable cause) {
        this(errorCode, detail, cause, null);
    }

    public CcarException(ErrorCode errorCode, String detail, Throwable cause, Long retryAfterSeconds) {
        super(detail != null ? detail : errorCode.title(), cause);
        this.errorCode = errorCode;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /** Seconds the client should wait before retrying (sent as {@code Retry-After}); null when not applicable. */
    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    /** Extra, client-safe members to add to the problem body (e.g. {@code buId}). */
    public CcarException with(String name, Object value) {
        properties.put(name, value);
        return this;
    }

    public Map<String, Object> getProperties() {
        return Collections.unmodifiableMap(properties);
    }
}
