// Client side of the shared CCAR error contract (RFC 9457 problem+json, see backend ccar-common ErrorCode).
// Every failed call - backend error, network failure, timeout - becomes an ApiError with an errorCode the UI
// can switch on, and the correlationId the user can quote so the request can be found in every service log.

export const AUTH_EXPIRED_EVENT = "ccar:auth-expired";

export class ApiError extends Error {
    constructor({status = 0, errorCode = null, title = "", detail = "", correlationId = null,
                    retryAfter = null, errors = [], path = ""}) {
        super(detail || title || "Request failed");
        this.name = "ApiError";
        this.status = status;            // HTTP status, 0 when no response was received
        this.errorCode = errorCode;      // e.g. KDB_UNAVAILABLE, TOKEN_EXPIRED, NETWORK_ERROR
        this.title = title;
        this.detail = detail;
        this.correlationId = correlationId;
        this.retryAfter = retryAfter;    // seconds, from Retry-After
        this.errors = errors;            // field errors for VALIDATION_FAILED
        this.path = path;
    }
}

// User-facing text per errorCode. Codes not listed fall back to a generic message by status.
const MESSAGES = {
    UNAUTHORIZED: "You need to sign in to see this.",
    TOKEN_EXPIRED: "Your session has expired. Please sign in again.",
    TOKEN_INVALID: "Your session is no longer valid. Please sign in again.",
    FORBIDDEN: "You don’t have access to this data.",

    BU_NOT_FOUND: "That business unit doesn’t exist.",
    BU_HIERARCHY_UNAVAILABLE: "The business-unit hierarchy isn’t available yet.",
    BU_HIERARCHY_INVALID: "The business-unit hierarchy data is invalid.",

    KDB_UNAVAILABLE: "The forecasting data store is unavailable right now.",
    KDB_POOL_EXHAUSTED: "The forecasting data store is busy.",
    KDB_TIMEOUT: "The forecasting data store took too long to respond.",
    KDB_QUERY_FAILED: "The forecasting data store couldn’t process this request.",

    DOWNSTREAM_UNAVAILABLE: "The service is unavailable right now.",
    DOWNSTREAM_TIMEOUT: "The service took too long to respond.",
    CIRCUIT_OPEN: "The service is temporarily unavailable after repeated failures.",
    SERVICE_UNAVAILABLE: "The service is unavailable right now.",

    ROUTE_NOT_FOUND: "This data isn’t available from the backend (endpoint not found).",
    RESOURCE_NOT_FOUND: "This data isn’t available from the backend (endpoint not found).",
    VALIDATION_FAILED: "The request was rejected as invalid.",
    INVALID_PARAMETER: "The request was rejected as invalid.",

    NETWORK_ERROR: "Can’t reach the server. Check your connection and that the backend is running.",
    CLIENT_TIMEOUT: "The server didn’t respond in time.",
    INVALID_RESPONSE: "The server sent a response the app couldn’t read.",
    INTERNAL_ERROR: "Something went wrong on the server.",
};

export function messageFor(error) {
    if (error instanceof ApiError) {
        if (error.errorCode && MESSAGES[error.errorCode]) return MESSAGES[error.errorCode];
        if (error.status >= 500) return MESSAGES.INTERNAL_ERROR;
        return error.detail || "The request failed.";
    }
    return "Something went wrong.";
}

export function isAuthError(error) {
    return error instanceof ApiError && error.status === 401;
}
