package com.ccar.common.web;

import com.ccar.common.error.ErrorCode;
import jakarta.validation.ConstraintViolationException;

/**
 * Bean-validation failures raised outside {@code @RequestBody} binding (e.g. entity validation on save),
 * registered only when Jakarta Validation is on the classpath. Rejected values are never echoed.
 */
public class ConstraintViolationErrorMapper implements CcarErrorMapper {

    @Override
    public MappedError map(Throwable ex) {
        return ex instanceof ConstraintViolationException
                ? MappedError.of(ErrorCode.VALIDATION_FAILED, "Request validation failed")
                : null;
    }
}
