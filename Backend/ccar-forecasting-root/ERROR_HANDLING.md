# Error handling & tracing

All CCAR services (api-gateway, ccar-core, user-service) and the UI share one error contract and one
correlation id. The shared code is in the `ccar-common` module.

## Error format (RFC 9457 `application/problem+json`)

```json
{
  "type": "urn:ccar:error:kdb-unavailable",
  "title": "Data store unavailable",
  "status": 503,
  "detail": "The forecasting data store is currently unavailable. Please retry shortly.",
  "instance": "/ccar/forecast/forecast-ccar",
  "errorCode": "KDB_UNAVAILABLE",
  "correlationId": "5c1d0e7e-...",
  "service": "ccar",
  "timestamp": "2026-11-11T10:15:30Z",
  "errors": [ { "field": "email", "message": "email must be valid" } ]
}
```

* `errorCode` is stable and machine-readable; the UI chooses its messages by it (`ccar_ui/src/api/errors.js`).
* `detail` is safe to show. It never contains queries, hosts, SQL or stack traces; those are logged server-side.
* `errors` is present only for `VALIDATION_FAILED`. Rejected values are never echoed back.
* 503 responses also send a `Retry-After` header. Gateway 401 responses also send `WWW-Authenticate`.

## Error-code catalogue (`com.ccar.common.error.ErrorCode`)

| Area | Codes |
|---|---|
| Request | `VALIDATION_FAILED` 400, `MALFORMED_REQUEST` 400, `INVALID_PARAMETER` 400, `BAD_REQUEST` 400, `RESOURCE_NOT_FOUND` 404, `METHOD_NOT_ALLOWED` 405, `NOT_ACCEPTABLE` 406, `CONFLICT` 409, `UNSUPPORTED_MEDIA_TYPE` 415 |
| Auth | `UNAUTHORIZED` 401, `TOKEN_INVALID` 401, `TOKEN_EXPIRED` 401, `INVALID_CREDENTIALS` 401, `FORBIDDEN` 403 |
| Users | `USER_NOT_FOUND` 404, `DUPLICATE_EMAIL` 409 |
| BU hierarchy | `BU_NOT_FOUND` 404, `BU_HIERARCHY_UNAVAILABLE` 503, `BU_HIERARCHY_INVALID` 500 |
| KDB | `KDB_UNAVAILABLE` 503, `KDB_POOL_EXHAUSTED` 503, `KDB_TIMEOUT` 504, `KDB_QUERY_FAILED` 502 |
| Gateway | `ROUTE_NOT_FOUND` 404, `DOWNSTREAM_UNAVAILABLE` 503, `DOWNSTREAM_TIMEOUT` 504, `CIRCUIT_OPEN` 503 |
| Generic | `SERVICE_UNAVAILABLE` 503, `INTERNAL_ERROR` 500 |

To add a code, add a constant to `ErrorCode` and, if users will see it, a message in the UI's `errors.js`.
Never rename a released code.

## Throwing errors in a service

```java
throw new CcarException(ErrorCode.BU_NOT_FOUND, "BU " + id + " not found").with("buId", id);
```

Each servlet service has a `@RestControllerAdvice GlobalExceptionHandler extends CcarExceptionHandler`. The base
class already handles `CcarException`, every Spring MVC exception (bad JSON, wrong parameter type, 405, 415,
unknown path, ...) and a catch-all 500. Errors raised outside MVC, such as in a filter, are shaped the same way
by `CcarErrorAttributes`.
**Log once:** the handler logs 5xx at ERROR with the stack trace and 4xx as one WARN line. Don't
catch-log-rethrow in services.

## KDB layer (`kdb-client-starter`)

| Failure | Exception | HTTP (ccar-core) | Connection |
|---|---|---|---|
| Pool max-wait exceeded | `KdbPoolExhaustedException` | 503 `KDB_POOL_EXHAUSTED` | - |
| Cannot connect / login refused | `KdbConnectionException` | 503 `KDB_UNAVAILABLE` | - |
| No answer within `kdb.query-timeout-millis` | `KdbTimeoutException` | 504 `KDB_TIMEOUT` | discarded |
| q error / unexpected result type | `KdbQueryException` | 502 `KDB_QUERY_FAILED` | kept |
| I/O error mid-query | `KdbConnectionException` | 503 `KDB_UNAVAILABLE` | discarded |

If the BU hierarchy fails to load, requests fail fast with `BU_HIERARCHY_UNAVAILABLE` and `Retry-After` for
`ccar.bu-hierarchy.retry-interval-ms` instead of hitting KDB again.

## Correlation id / tracing

1. The UI sends `X-Correlation-Id` (a new UUID per call, reused across retries).
2. The gateway's `CorrelationIdWebFilter` accepts it, or replaces it if it is missing or not log-safe
   (`[A-Za-z0-9._:-]{1,128}`). It then forwards it downstream, echoes it on every response and prefixes its log
   lines with `[id]`.
3. Each service's `CorrelationIdFilter` (in ccar-common, auto-configured) puts it in the MDC, so log lines show
   it through `logging.pattern.level=%5p [%X{correlationId:-}]`. It also echoes it and adds it to error bodies.
4. Async work keeps the id: `MdcTaskDecorator` for `@Async`, `KdbTemplate.executeAsync`. Outbound calls keep it
   too: register `CorrelationIdClientInterceptor` on a `RestClient`/`RestTemplate`.

To trace a user-reported error, search all service logs for the `Reference` / `correlationId` shown in the UI.

## Gateway resilience

* Timeouts: connect 2 s. Per-route response timeout: ccar-core 35 s, user-service 10 s
  (`ccar.gateway.services.*`).
* A circuit breaker per route sends failures to `/fallback/{service}`, which returns 503
  `DOWNSTREAM_UNAVAILABLE` / `CIRCUIT_OPEN` or 504 `DOWNSTREAM_TIMEOUT`.
* Retry: GET on ccar-core only, and only on connection refused (the request never reached the service).
* 4xx/5xx responses from services are passed through unchanged; they already use this format.

## Build

`ccar-common` is a reactor module. Build from this directory with `./ccar-core/mvnw install` (or `mvn install`).
Building `ccar-api-gateway` on its own requires `ccar-common` to be installed in the local repository first.
