package com.ccar.common.error;

import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.time.Instant;

/**
 * Builds the one error format used by every CCAR service: RFC 9457 {@code application/problem+json}
 * plus CCAR extension members.
 *
 * <pre>
 * {
 *   "type":          "urn:ccar:error:user-not-found",
 *   "title":         "User not found",
 *   "status":        404,
 *   "detail":        "User not found with id: 42",
 *   "instance":      "/users/42",
 *   "errorCode":     "USER_NOT_FOUND",
 *   "correlationId": "5c1d...",
 *   "service":       "user-service",
 *   "timestamp":     "2026-11-11T10:15:30Z",
 *   "errors":        [ { "field": "email", "message": "email must be valid" } ]   (validation only)
 * }
 * </pre>
 *
 * Only uses {@link ProblemDetail} API present in both Spring 6 (gateway) and Spring 7 (services).
 */
public final class ProblemDetailFactory {

    public static final String ERROR_CODE = "errorCode";
    public static final String CORRELATION_ID = "correlationId";
    public static final String SERVICE = "service";
    public static final String TIMESTAMP = "timestamp";
    public static final String ERRORS = "errors";

    private ProblemDetailFactory() {
    }

    public static ProblemDetail create(ErrorCode code, String detail, String instance,
                                       String correlationId, String service) {
        ProblemDetail problem = ProblemDetail.forStatus(code.status());
        enrich(problem, code, instance, correlationId, service);
        problem.setDetail(detail != null ? detail : code.title());
        return problem;
    }

    /**
     * Stamps CCAR members onto an existing problem (e.g. one Spring MVC built for a framework exception),
     * keeping its {@code detail} and status.
     */
    public static ProblemDetail enrich(ProblemDetail problem, ErrorCode code, String instance,
                                       String correlationId, String service) {
        problem.setType(code.type());
        problem.setTitle(code.title());
        if (instance != null) {
            problem.setInstance(URI.create(safePath(instance)));
        }
        problem.setProperty(ERROR_CODE, code.name());
        problem.setProperty(CORRELATION_ID, correlationId);
        problem.setProperty(SERVICE, service);
        problem.setProperty(TIMESTAMP, Instant.now().toString());
        return problem;
    }

    /** Request paths can contain characters URI.create rejects (e.g. spaces); keep the instance well-formed. */
    private static String safePath(String path) {
        try {
            URI.create(path);
            return path;
        } catch (IllegalArgumentException e) {
            return path.replaceAll("[^A-Za-z0-9/._~-]", "_");
        }
    }
}
