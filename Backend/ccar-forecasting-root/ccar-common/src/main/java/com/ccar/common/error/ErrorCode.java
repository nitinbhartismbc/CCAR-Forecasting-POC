package com.ccar.common.error;

import java.net.URI;
import java.util.Locale;

/**
 * The single error-code catalogue shared by every CCAR service (gateway, ccar-core, user-service) and the UI.
 * <p>
 * The enum constant name is the stable, machine-readable {@code errorCode} sent to clients; the UI keys its
 * user-facing messages off it. Never rename a constant once released - add a new one instead.
 * <p>
 * Plain Java on purpose (status is an {@code int}) so it can be used from both the Boot 4 servlet services
 * and the Boot 3 reactive gateway without dragging a Spring version along.
 */
public enum ErrorCode {

    // ---- generic request errors -------------------------------------------------------------------------
    VALIDATION_FAILED(400, "Validation failed"),
    MALFORMED_REQUEST(400, "Malformed request"),
    INVALID_PARAMETER(400, "Invalid request parameter"),
    RESOURCE_NOT_FOUND(404, "Resource not found"),
    METHOD_NOT_ALLOWED(405, "Method not allowed"),
    NOT_ACCEPTABLE(406, "Not acceptable"),
    CONFLICT(409, "Conflict"),
    UNSUPPORTED_MEDIA_TYPE(415, "Unsupported media type"),
    BAD_REQUEST(400, "Bad request"),

    // ---- authentication ---------------------------------------------------------------------------------
    UNAUTHORIZED(401, "Authentication required"),
    TOKEN_INVALID(401, "Invalid access token"),
    TOKEN_EXPIRED(401, "Access token expired"),
    INVALID_CREDENTIALS(401, "Invalid credentials"),
    FORBIDDEN(403, "Forbidden"),

    // ---- user-service -----------------------------------------------------------------------------------
    USER_NOT_FOUND(404, "User not found"),
    DUPLICATE_EMAIL(409, "Email already registered"),

    // ---- ccar-core: BU hierarchy ------------------------------------------------------------------------
    BU_NOT_FOUND(404, "Business unit not found"),
    BU_HIERARCHY_UNAVAILABLE(503, "BU hierarchy unavailable"),
    BU_HIERARCHY_INVALID(500, "BU hierarchy data invalid"),

    // ---- ccar-core: KDB data store ----------------------------------------------------------------------
    KDB_UNAVAILABLE(503, "Data store unavailable"),
    KDB_POOL_EXHAUSTED(503, "Data store busy"),
    KDB_TIMEOUT(504, "Data store timeout"),
    KDB_QUERY_FAILED(502, "Data store query failed"),

    // ---- api-gateway ------------------------------------------------------------------------------------
    ROUTE_NOT_FOUND(404, "No route for request"),
    DOWNSTREAM_UNAVAILABLE(503, "Service unavailable"),
    DOWNSTREAM_TIMEOUT(504, "Service timeout"),
    CIRCUIT_OPEN(503, "Service temporarily unavailable"),

    // ---- catch-all --------------------------------------------------------------------------------------
    SERVICE_UNAVAILABLE(503, "Service unavailable"),
    INTERNAL_ERROR(500, "Internal server error");

    /** Prefix of the RFC 9457 {@code type} URI; the full type is e.g. {@code urn:ccar:error:user-not-found}. */
    public static final String TYPE_PREFIX = "urn:ccar:error:";

    private final int status;
    private final String title;

    ErrorCode(int status, String title) {
        this.status = status;
        this.title = title;
    }

    public int status() {
        return status;
    }

    public String title() {
        return title;
    }

    public URI type() {
        return URI.create(TYPE_PREFIX + name().toLowerCase(Locale.ROOT).replace('_', '-'));
    }

    public boolean isServerError() {
        return status >= 500;
    }

    /** Best generic code for a bare HTTP status (used for framework exceptions that carry only a status). */
    public static ErrorCode fromStatus(int status) {
        return switch (status) {
            case 400 -> BAD_REQUEST;
            case 401 -> UNAUTHORIZED;
            case 403 -> FORBIDDEN;
            case 404 -> RESOURCE_NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 406 -> NOT_ACCEPTABLE;
            case 409 -> CONFLICT;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            case 502, 503 -> SERVICE_UNAVAILABLE;
            case 504 -> DOWNSTREAM_TIMEOUT;
            default -> status >= 400 && status < 500 ? BAD_REQUEST : INTERNAL_ERROR;
        };
    }
}
