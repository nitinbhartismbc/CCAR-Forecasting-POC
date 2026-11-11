package com.ccar.user.exception;

import com.ccar.common.error.ErrorCode;
import com.ccar.common.web.CcarExceptionHandler;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

/**
 * user-service error mapping.
 * <p>
 * Everything shared - {@code UserNotFoundException} (404), {@code DuplicateEmailException} (409),
 * {@code InvalidCredentialsException} (401), bean validation (400 with every field error), malformed JSON,
 * wrong parameter types, 405/415, unknown paths and the generic 500 - is inherited from
 * {@link CcarExceptionHandler}, so the body always matches the HTTP status and the shared problem format.
 * This class only adds persistence-specific mappings.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends CcarExceptionHandler {

    /** Unique-constraint race (e.g. two registrations with the same email at once). DB details are not exposed. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrity(DataIntegrityViolationException ex, WebRequest request) {
        return respond(ex, ErrorCode.CONFLICT,
                problem(ErrorCode.CONFLICT, "The request conflicts with existing data.", request), new HttpHeaders());
    }

    /** Bean-validation failures raised outside @RequestBody binding (e.g. entity validation on save). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        return respond(ex, ErrorCode.VALIDATION_FAILED,
                problem(ErrorCode.VALIDATION_FAILED, "Request validation failed", request), new HttpHeaders());
    }

    /** Database unreachable / other persistence failures: 503, never the SQL or driver message. */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Object> handleDataAccess(DataAccessException ex, WebRequest request) {
        return respond(ex, ErrorCode.SERVICE_UNAVAILABLE,
                problem(ErrorCode.SERVICE_UNAVAILABLE, "The user store is currently unavailable.", request),
                new HttpHeaders());
    }
}
