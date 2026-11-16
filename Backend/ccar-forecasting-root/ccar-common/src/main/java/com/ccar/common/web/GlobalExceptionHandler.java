package com.ccar.common.web;

import com.ccar.common.error.CcarException;
import com.ccar.common.error.ErrorCode;
import com.ccar.common.error.ProblemDetailFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The one {@code @RestControllerAdvice} of every CCAR servlet service (ccar-hierarchy-service, ccar-user-service).
 * Services do not declare their own: this class is registered by {@link CcarWebAutoConfiguration} and gives
 * <ul>
 *   <li>the shared problem format ({@link ProblemDetailFactory}) for <b>every</b> error - application
 *       {@link CcarException}s, all Spring MVC framework exceptions (bad JSON, wrong types, 405, 415, unknown
 *       path, ...) and unexpected exceptions;</li>
 *   <li>store-specific mappings through {@link CcarErrorMapper} beans, auto-registered when the store is on the
 *       classpath: {@link KdbErrorMapper} (kdb-starter), {@link PersistenceErrorMapper} (Spring Data / JPA),
 *       {@link ConstraintViolationErrorMapper} (Bean Validation). A service adds its own mapping by declaring a
 *       {@code CcarErrorMapper} bean;</li>
 *   <li>a catch-all that never leaks internals (generic detail + correlation id);</li>
 *   <li>logging in exactly one place: 5xx at ERROR with stack trace, 4xx at WARN as a single line.</li>
 * </ul>
 * The reactive api-gateway uses {@code com.ccar.common.reactive.ReactiveGlobalExceptionHandler} instead.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    public static final String GENERIC_SERVER_ERROR =
            "An unexpected error occurred. Quote the correlationId when reporting this issue.";

    /** Cause-chain depth searched for a mappable exception. */
    private static final int MAX_CAUSE_DEPTH = 10;

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final String serviceName;
    private final List<CcarErrorMapper> errorMappers;

    public GlobalExceptionHandler(String serviceName, List<CcarErrorMapper> errorMappers) {
        this.serviceName = serviceName;
        this.errorMappers = List.copyOf(errorMappers);
    }

    // ---------------------------------------------------------------------------------------------------
    // Application exceptions
    // ---------------------------------------------------------------------------------------------------

    @ExceptionHandler(CcarException.class)
    public ResponseEntity<Object> handleCcarException(CcarException ex, WebRequest request) {
        HttpHeaders headers = new HttpHeaders();
        if (ex.getRetryAfterSeconds() != null) {
            headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()));
        }
        ProblemDetail problem = problem(ex.getErrorCode(), ex.getMessage(), request);
        ex.getProperties().forEach(problem::setProperty);
        return respond(ex, ex.getErrorCode(), problem, headers);
    }

    /**
     * Everything without a more specific handler: first offered to the {@link CcarErrorMapper}s (the exception
     * and its causes), otherwise a 500 with a generic, non-leaking detail.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        CcarErrorMapper.MappedError mapped = map(ex);
        if (mapped != null) {
            HttpHeaders headers = new HttpHeaders();
            if (mapped.retryAfterSeconds() != null) {
                headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(mapped.retryAfterSeconds()));
            }
            return respond(ex, mapped.code(), problem(mapped.code(), mapped.detail(), request), headers);
        }
        return respond(ex, ErrorCode.INTERNAL_ERROR,
                problem(ErrorCode.INTERNAL_ERROR, GENERIC_SERVER_ERROR, request), new HttpHeaders());
    }

    // ---------------------------------------------------------------------------------------------------
    // Spring MVC framework exceptions (all funnel through handleExceptionInternal)
    // ---------------------------------------------------------------------------------------------------

    /** Reports every field error, not just the first one. Rejected values are never echoed (may be secrets). */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        List<Map<String, String>> errors = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.add(error(fe.getField(), fe.getDefaultMessage()));
        }
        ex.getBindingResult().getGlobalErrors()
                .forEach(ge -> errors.add(error(ge.getObjectName(), ge.getDefaultMessage())));

        ProblemDetail body = ex.getBody();
        body.setDetail("Request validation failed with " + errors.size() + " error(s)");
        body.setProperty(ProblemDetailFactory.ERRORS, errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ErrorCode code = mapFrameworkException(ex, statusCode);
        ProblemDetail problem = body instanceof ProblemDetail pd
                ? pd
                : ProblemDetail.forStatusAndDetail(statusCode, code.title());
        ProblemDetailFactory.enrich(problem, code, path(request), CorrelationIdFilter.current(request), serviceName);
        logException(ex, statusCode.value(), code);
        return super.handleExceptionInternal(ex, problem, headers, statusCode, request);
    }

    /** Maps a framework exception to the catalogue. Subclasses may override to refine. */
    protected ErrorCode mapFrameworkException(Exception ex, HttpStatusCode status) {
        if (status.is5xxServerError()) {
            return ErrorCode.fromStatus(status.value());
        }
        if (ex instanceof MethodArgumentNotValidException || ex instanceof HandlerMethodValidationException) {
            return ErrorCode.VALIDATION_FAILED;
        }
        if (ex instanceof HttpMessageNotReadableException) {
            return ErrorCode.MALFORMED_REQUEST;
        }
        if (ex instanceof TypeMismatchException || ex instanceof ServletRequestBindingException) {
            return ErrorCode.INVALID_PARAMETER;
        }
        if (ex instanceof HttpRequestMethodNotSupportedException) {
            return ErrorCode.METHOD_NOT_ALLOWED;
        }
        if (ex instanceof HttpMediaTypeNotSupportedException) {
            return ErrorCode.UNSUPPORTED_MEDIA_TYPE;
        }
        if (ex instanceof HttpMediaTypeNotAcceptableException) {
            return ErrorCode.NOT_ACCEPTABLE;
        }
        if (ex instanceof NoResourceFoundException || ex instanceof NoHandlerFoundException) {
            return ErrorCode.RESOURCE_NOT_FOUND;
        }
        return ErrorCode.fromStatus(status.value());
    }

    private CcarErrorMapper.MappedError map(Throwable ex) {
        int depth = 0;
        for (Throwable t = ex; t != null && depth < MAX_CAUSE_DEPTH; t = t.getCause(), depth++) {
            for (CcarErrorMapper mapper : errorMappers) {
                CcarErrorMapper.MappedError mapped = mapper.map(t);
                if (mapped != null) {
                    return mapped;
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------------
    // Helpers (protected so a service could still extend this class if it ever needs to)
    // ---------------------------------------------------------------------------------------------------

    /** Builds a problem in the shared format for the current request. */
    protected ProblemDetail problem(ErrorCode code, String detail, WebRequest request) {
        return ProblemDetailFactory.create(code, detail, path(request), CorrelationIdFilter.current(request),
                serviceName);
    }

    /** Logs once and returns the problem with the code's status. */
    protected ResponseEntity<Object> respond(Exception ex, ErrorCode code, ProblemDetail problem,
                                             HttpHeaders headers) {
        logException(ex, code.status(), code);
        return ResponseEntity.status(code.status()).headers(headers).body(problem);
    }

    /** The single place errors are logged: 5xx with stack trace, 4xx as one WARN line. */
    protected void logException(Exception ex, int status, ErrorCode code) {
        if (status >= 500) {
            log.error("Request failed: status={} errorCode={} exception={}", status, code, ex.toString(), ex);
        } else {
            log.warn("Request rejected: status={} errorCode={} reason={}", status, code, reason(ex));
        }
    }

    /**
     * One-line, log-safe reason for a 4xx. Validation messages are reduced to field names, because Spring's
     * message includes the rejected values - which can be passwords.
     */
    protected String reason(Exception ex) {
        if (ex instanceof MethodArgumentNotValidException manv) {
            return "invalid fields " + manv.getBindingResult().getFieldErrors().stream()
                    .map(FieldError::getField).distinct().toList();
        }
        String message = String.valueOf(ex.getMessage());
        return message.length() > 300 ? message.substring(0, 300) + "...(truncated)" : message;
    }

    protected String serviceName() {
        return serviceName;
    }

    private static Map<String, String> error(String field, String message) {
        Map<String, String> e = new LinkedHashMap<>();
        e.put("field", field);
        e.put("message", message);
        return e;
    }

    private static String path(WebRequest request) {
        return request instanceof ServletWebRequest swr ? swr.getRequest().getRequestURI() : null;
    }
}
