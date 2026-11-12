# CCAR-Forecasting-POC

Backend for the CCAR Forecasting POC: a multi-module Maven project with an API gateway, two Spring Boot
services (forecast data from KDB+, and users / login), a reusable KDB+ client starter and a shared
error-handling and tracing library.

- Error contract, error codes and tracing: [ERROR_HANDLING.md](ERROR_HANDLING.md)
- UI: [`../../ccar_ui`](../../ccar_ui) (React + Vite)

---

## Contents

1. [Architecture](#architecture)
2. [Modules](#modules)
3. [Ports at a glance](#ports-at-a-glance)
4. [Prerequisites](#prerequisites)
5. [Build and test](#build-and-test)
6. [Run locally](#run-locally)
7. [Step-by-step API walkthrough (login first)](#step-by-step-api-walkthrough-login-first)
8. [Endpoint reference](#endpoint-reference)
9. [Errors and tracing](#errors-and-tracing)
10. [Configuration](#configuration)
11. [Known gaps](#known-gaps)

---

## Architecture

```
                        Authorization: Bearer <JWT>
                        X-Correlation-Id: <id>
  ┌──────────────┐      ──────────────────────────▶   ┌───────────────────────────────┐
  │  CCAR UI     │                                    │  ccar-api-gateway   :8089     │
  │  :5173       │                                    │  1 CorrelationIdWebFilter     │
  └──────────────┘                                    │  2 JwtAuthenticationFilter    │
                                                      │  3 CircuitBreaker + Retry     │
                                                      │  4 route (timeouts)           │
                                                      │  on failure: Fallback /       │
                                                      │  GatewayErrorWebExceptionHdlr │
                                                      └──────┬─────────────────┬──────┘
                                     /ccar/forecast/**       │                 │   /users, /users/**
                                     (35 s timeout)          ▼                 ▼   (10 s timeout)
                              ┌──────────────────────────────┐   ┌──────────────────────────────┐
                              │ ccar-core   :8081  /ccar     │   │ ccar-user-service   :8082    │
                              │ CcarForecastController       │   │ UserController               │
                              │ BuHierarchyService (cache)   │   │ UserServiceImpl (BCrypt,JWT) │
                              │ KdbService                   │   │ GlobalExceptionHandler       │
                              │ GlobalExceptionHandler       │   └──────────────┬───────────────┘
                              │   └ kdb-client-starter       │                  │ JPA
                              │     (KdbTemplate + pool)     │                  ▼
                              └──────────────┬───────────────┘           ┌─────────────┐
                                             │ q IPC (30 s read timeout) │ H2 (memory) │
                                             ▼                           └─────────────┘
                                      ┌─────────────┐
                                      │ KDB+  :5173 │  forecast_ccar, BU_HIERARCHY
                                      └─────────────┘

  ccar-common (shared library, used by gateway, ccar-core and user-service):
  ErrorCode catalogue · RFC 9457 problem+json · correlation-id filter · base exception handler
```

- Every production request enters through the **gateway on :8089**. It assigns or keeps the correlation id,
  validates the JWT (issued by user-service with the same HS512 secret) and routes to the service.
- ccar-core reads KDB+ only through the pooled `KdbTemplate` from `kdb-client-starter`.
- All services return errors in one format (`application/problem+json`) with an `errorCode` and the
  `correlationId`.

## Modules

| Module | Artifact | Type | Port | Spring Boot | Java |
|---|---|---|---|---|---|
| (root) | `com.ccar:ccar-forecasting-parent:1.0.0` | Parent / aggregator POM | – | 4.1.2 | 21 |
| [`ccar-common`](ccar-common) | `com.ccar:ccar-common:1.0.0` | Library (errors + tracing) | – | 4.1.2 (optional deps) | 17 |
| [`kdb-client-starter`](kdb-client-starter) | `com.iris.kdb:kdb-starter:1.0.0` | Library (Spring Boot starter) | – | 4.1.2 | 17 |
| [`ccar-core`](ccar-core) | `com.example:ccar-core:0.0.1-SNAPSHOT` | Spring Boot application | 8081 | 4.1.2 | 21 |
| [`ccar-user-service`](ccar-user-service) | `com.ccar:ccar-user-service:1.0.0` | Spring Boot application | 8082 | 4.1.2 | 21 |
| [`ccar-api-gateway`](ccar-api-gateway) | `com.ccar:ccar-api-gateway:0.0.1-SNAPSHOT` | Spring Cloud Gateway application | 8089 | 3.4.5 | 21 |

Reactor build order: `ccar-common` → `kdb-client-starter` → `ccar-core` → `ccar-user-service` → `ccar-api-gateway`.

> `ccar-api-gateway` is a module of the root POM but uses `spring-boot-starter-parent` **3.4.5** with Spring
> Cloud **2024.0.1**, because Spring Cloud Gateway 2024.0.x does not support Spring Boot 4.x. `ccar-common`
> declares all its Spring dependencies as optional, so it is safe on both Boot 3 and Boot 4.

### `ccar-common`

Shared contract for errors and tracing.

- `error/ErrorCode`: the catalogue of error codes (status + title) used by every service and the UI.
- `error/ProblemDetailFactory`: builds the RFC 9457 problem body with `errorCode`, `correlationId`, `service`
  and `timestamp`.
- `error/CcarException`: base exception for application errors (`new CcarException(ErrorCode.X, "detail")`).
- `tracing/CorrelationId`: header name, MDC key and validation of incoming ids.
- `web/` (servlet apps, auto-configured): `CorrelationIdFilter`, `CcarExceptionHandler` (base
  `@RestControllerAdvice`), `CcarErrorAttributes` (same format for errors outside MVC), `MdcTaskDecorator`,
  `CorrelationIdClientInterceptor`.

### `kdb-client-starter`

Reusable Spring Boot starter for KDB+ access, picked up by adding it as a dependency.

- **Auto-configuration** (`KdbAutoConfiguration`, `KdbProperties`): binds `kdb.*` and registers the pool,
  template and metrics controller.
- **Connection pooling** (`KdbConnectionPool`, `KdbConnectionFactory`): Apache Commons Pool2, validated on
  borrow, socket read timeout `kdb.query-timeout-millis`.
- **Query execution** (`KdbTemplate`): `execute(query)`, `execute(query, Class)`, `executeAsync(query)`,
  `ping()`.
- **Typed failures**: `KdbConnectionException`, `KdbPoolExhaustedException`, `KdbTimeoutException`,
  `KdbQueryException` (all extend `KdbException`). Healthy connections are returned to the pool after a q
  error; broken ones are discarded.
- **Monitoring**: `GET /kdb/health`, `GET /kdb/metrics`.
- **Query logging** (`KdbLoggingAspect`): duration, slow queries at WARN, failures with the truncated query.

See [`kdb-client-starter/README.md`](kdb-client-starter/README.md) for full usage.

### `ccar-core`

Forecast data and the Business Unit (BU) hierarchy, read from KDB+. Port **8081**, context path **`/ccar`**.

- `controller/CcarForecastController`: REST endpoints under `/forecast`.
- `service/KdbService`: runs `select from forecast_ccar` and returns it as JSON.
- `service/BuHierarchyService`: loads `BU_HIERARCHY` from KDB+ once, builds and caches the tree. While KDB+
  is down it answers 503 + `Retry-After` for `ccar.bu-hierarchy.retry-interval-ms` instead of retrying on
  every request; a failed reload keeps the previous tree.
- `exception/GlobalExceptionHandler`: maps KDB failures to 503 / 504 / 502.
- `logging/LoggingAspect`: method entry/exit timings (DEBUG shows args/results).
- `client/KdbClient`: standalone `main()` for testing KDB connectivity by hand.

### `ccar-user-service`

Registration, profiles and login; issues the JWT the gateway validates. Port **8082**, H2 in-memory DB.

- `UserController` / `UserServiceImpl`: CRUD on users, `POST /users/login` returns a JWT (HS512, 1 h).
- Passwords are stored with BCrypt.
- `exception/GlobalExceptionHandler`: 404 `USER_NOT_FOUND`, 409 `DUPLICATE_EMAIL`, 401
  `INVALID_CREDENTIALS`, 400 validation (every field listed), 409/503 for database errors.

### `ccar-api-gateway`

Single entry point, built on Spring Cloud Gateway. Port **8089**.

- `logging/CorrelationIdWebFilter`: accepts or creates `X-Correlation-Id`, forwards it, echoes it on every
  response and logs one line in and one line out per request.
- `security/JwtAuthenticationFilter`: requires `Authorization: Bearer <JWT>` except on `POST /users` and
  `POST /users/login`. Returns 401 `UNAUTHORIZED`, `TOKEN_EXPIRED` or `TOKEN_INVALID` with a
  `WWW-Authenticate` header.
- Routes with a Resilience4j circuit breaker each (`config/ResilienceConfig`), fallback to
  `error/FallbackController` (503 / 504). GETs to ccar-core are retried only when the connection is refused.
- `error/GatewayErrorWebExceptionHandler`: unknown route 404, unexpected 500, all in the shared format.

## Ports at a glance

| Component | URL |
|---|---|
| API gateway (use this) | `http://localhost:8089` |
| ccar-core (direct) | `http://localhost:8081/ccar` |
| ccar-user-service (direct) | `http://localhost:8082` |
| KDB+ | as configured in `ccar-core/src/main/resources/application.properties` (`kdb.host`, `kdb.port`) |
| UI (Vite dev server) | `http://localhost:5173` |

## Prerequisites

- Java 21 (the two libraries target 17)
- Maven 3.8+, or the bundled wrapper `./ccar-core/mvnw`
- A running KDB+ instance with the `forecast_ccar` and `BU_HIERARCHY` tables
- `curl` (examples below use bash / Git Bash; PowerShell equivalents are given for the login step)

## Build and test

From this directory:

```bash
./ccar-core/mvnw clean install      # or: mvn clean install
```

This builds all six modules and runs the test suite (unit tests, `@WebMvcTest` handler tests, simulated KDB
failures, and gateway tests for 401 / timeout / downstream-down).

> Building `ccar-api-gateway` on its own (from its folder) requires `ccar-common` to be installed in the
> local Maven repository first, so run the root build at least once.

## Run locally

Start the components in this order, each in its own terminal.

**1. KDB+ tables**

```
q -p 5173
q)/ forecast_ccar: paste ccar-core/src/main/resources/KDB_Tables.txt
q)\l BU_HIERARCHY_LOAD.q          / BU_HIERARCHY from BU_HIERARCHY1.csv (see the header of the script)
```

`BU_HIERARCHY_INSERT_100.q` loads only the first 100 rows, useful for a quick test.

**2. ccar-core**

```bash
cd ccar-core && ./mvnw spring-boot:run
```

On startup it loads the BU hierarchy from KDB+ and logs `BU hierarchy loaded: N node(s) ...`. If KDB+ is not
reachable it logs the error and keeps running; hierarchy requests then return 503 until KDB+ is back.

**3. ccar-user-service**

```bash
cd ccar-user-service && ../ccar-core/mvnw spring-boot:run
```

**4. ccar-api-gateway**

```bash
cd ccar-api-gateway && ./mvnw spring-boot:run
```

**5. UI (optional)**

```bash
cd ../../ccar_ui && npm install && npm run dev
```

Logs are written to `logs/ccar.log` (ccar-core), `logs/ccar-api-gateway.log` (gateway) and the console
(user-service). Every line carries the correlation id.

---

## Step-by-step API walkthrough (login first)

All calls go through the gateway on **:8089**. Only registration and login are public; everything else needs
the JWT from step 2.

> **About the seeded users in `data.sql`:** their `password_hash` values are SHA-256 digests from an older
> version, but login now checks passwords with BCrypt, so those users **cannot log in**. Register a new user
> first (step 1). The H2 database is in memory, so repeat step 1 after each restart of user-service.

### Step 1 – Register a user (public)

```bash
curl -i -X POST http://localhost:8089/users \
  -H "Content-Type: application/json" \
  -d '{"name":"Demo Analyst","email":"demo.analyst@example.com","password":"Demo@12345","phone":"555-0199"}'
```

`201 Created`

```json
{
  "id": 1,
  "name": "Demo Analyst",
  "email": "demo.analyst@example.com",
  "address": null,
  "phone": "555-0199",
  "createdAt": "2026-11-11T16:05:12.345"
}
```

A second registration with the same email returns `409` with `"errorCode": "DUPLICATE_EMAIL"`.

### Step 2 – Log in and get the JWT (public)

```bash
curl -s -X POST http://localhost:8089/users/login \
  -H "Content-Type: application/json" \
  -d '{"email":"demo.analyst@example.com","password":"Demo@12345"}'
```

`200 OK`

```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJkZW1vLmFuYWx5c3RAZXhhbXBsZS5jb20i...",
  "expiresIn": 3600,
  "userId": 1
}
```

A wrong password returns `401` with `"errorCode": "INVALID_CREDENTIALS"` (same answer for an unknown email).

### Step 3 – Keep the token for the next calls

bash / Git Bash:

```bash
TOKEN=$(curl -s -X POST http://localhost:8089/users/login \
  -H "Content-Type: application/json" \
  -d '{"email":"demo.analyst@example.com","password":"Demo@12345"}' \
  | sed -E 's/.*"token":"([^"]+)".*/\1/')
echo "$TOKEN"
```

PowerShell:

```powershell
$login = Invoke-RestMethod -Method Post -Uri http://localhost:8089/users/login `
  -ContentType "application/json" `
  -Body '{"email":"demo.analyst@example.com","password":"Demo@12345"}'
$TOKEN = $login.token
$H = @{ Authorization = "Bearer $TOKEN" }
```

The token is valid for 1 hour. After that every call returns `401 TOKEN_EXPIRED`; log in again.

### Step 4 – Get the forecast data

```bash
curl -s http://localhost:8089/ccar/forecast/forecast-ccar \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Correlation-Id: demo-001"
```

PowerShell: `Invoke-RestMethod http://localhost:8089/ccar/forecast/forecast-ccar -Headers $H`

`200 OK` (shortened)

```json
{
  "forecast_ccar": {
    "data": [
      { "OP_CODE_ID": 10000001, "ACCOUNT_ID": 210001, "CCY_CODE": "CHF", "PORTFOLIO_ID": "PORT_0001",
        "T0_DATE": "2025-12-31", "T0": 100000, "T_1": 100001, "...": "...", "T_36": 100036 }
    ]
  }
}
```

### Step 5 – Get the top of the BU hierarchy

```bash
curl -s "http://localhost:8089/ccar/forecast/bu-hierarchy?depth=1" \
  -H "Authorization: Bearer $TOKEN"
```

`depth` limits how many levels below the root are returned (omit it for the whole tree).
`childCount` is the real number of children, even when `children` is cut off by `depth`.

```json
{
  "id": 1, "name": "ALL_BANK", "levelId": 1, "parentId": null, "childCount": 2,
  "children": [
    { "id": 2,  "name": "North America", "levelId": 2, "parentId": 1, "childCount": 1, "children": [] },
    { "id": 30, "name": "Europe",        "levelId": 2, "parentId": 1, "childCount": 4, "children": [] }
  ]
}
```

### Step 6 – Open one BU (node plus direct children)

```bash
curl -s "http://localhost:8089/ccar/forecast/bu-hierarchy/2?depth=1" \
  -H "Authorization: Bearer $TOKEN"
```

### Step 7 – Lazy-load the children of a BU (for expanding a tree node)

```bash
curl -s http://localhost:8089/ccar/forecast/bu-hierarchy/2/children \
  -H "Authorization: Bearer $TOKEN"
```

Returns a JSON array of nodes without grandchildren. An unknown id returns `404` with
`"errorCode": "BU_NOT_FOUND"`.

### Step 8 – Reload the BU hierarchy after the KDB table changed

```bash
curl -s -X POST http://localhost:8089/ccar/forecast/bu-hierarchy/reload \
  -H "Authorization: Bearer $TOKEN"
```

```json
{ "nodes": 1342, "loadedAt": "2026-11-11T10:42:07.118Z" }
```

### Step 9 – Manage users

```bash
# list all users
curl -s http://localhost:8089/users -H "Authorization: Bearer $TOKEN"

# get one user
curl -s http://localhost:8089/users/1 -H "Authorization: Bearer $TOKEN"

# update a user (name, email and password are required)
curl -s -X PUT http://localhost:8089/users/1 \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Demo Analyst","email":"demo.analyst@example.com","password":"Demo@12345","address":"1 Main St"}'

# delete a user -> 204 No Content
curl -i -X DELETE http://localhost:8089/users/1 -H "Authorization: Bearer $TOKEN"
```

### Step 10 – See the error format (optional)

```bash
# no token -> 401 UNAUTHORIZED
curl -i http://localhost:8089/ccar/forecast/bu-hierarchy

# unknown BU -> 404 BU_NOT_FOUND
curl -s http://localhost:8089/ccar/forecast/bu-hierarchy/999999 -H "Authorization: Bearer $TOKEN"
```

Example `401` body (`Content-Type: application/problem+json`, header `WWW-Authenticate: Bearer realm="ccar"`):

```json
{
  "type": "urn:ccar:error:unauthorized",
  "title": "Authentication required",
  "status": 401,
  "detail": "A Bearer access token is required.",
  "instance": "/ccar/forecast/bu-hierarchy",
  "errorCode": "UNAUTHORIZED",
  "correlationId": "0b6f5a4e-2c1d-4b8e-9a43-7d2f1e6c9b10",
  "service": "ccar-api-gateway",
  "timestamp": "2026-11-11T10:45:00.123Z"
}
```

If KDB+ is down, step 4 returns `503` with `"errorCode": "KDB_UNAVAILABLE"` and `Retry-After: 30`. Search
the logs for the `correlationId` to see the full trail across services.

### Postman

All of the above, plus about 40 negative and tracing cases with automated checks, is in
[`postman/`](postman): import the collection and the `CCAR Local` environment, then run the collection in
order. See [`postman/README.md`](postman/README.md).

---

## Endpoint reference

### Through the gateway (`http://localhost:8089`)

| Method | Path | Auth | Description | Success |
|---|---|---|---|---|
| `POST` | `/users` | public | Register a user | `201` user |
| `POST` | `/users/login` | public | Log in, returns `{token, expiresIn, userId}` | `200` |
| `GET` | `/users` | Bearer | List users | `200` array |
| `GET` | `/users/{id}` | Bearer | Get a user | `200` user |
| `PUT` | `/users/{id}` | Bearer | Update a user | `200` user |
| `DELETE` | `/users/{id}` | Bearer | Delete a user | `204` |
| `GET` | `/ccar/forecast/forecast-ccar` | Bearer | `forecast_ccar` table as JSON | `200` |
| `GET` | `/ccar/forecast/bu-hierarchy?depth={n}` | Bearer | BU tree from the root (`depth` optional, ≥ 0) | `200` node |
| `GET` | `/ccar/forecast/bu-hierarchy/{buId}?depth={n}` | Bearer | Subtree of one BU (`depth` default 1) | `200` node |
| `GET` | `/ccar/forecast/bu-hierarchy/{buId}/children` | Bearer | Direct children of one BU | `200` array |
| `POST` | `/ccar/forecast/bu-hierarchy/reload` | Bearer | Reload the BU tree from KDB+ | `200 {nodes, loadedAt}` |

Request bodies:

| Endpoint | Body | Rules |
|---|---|---|
| `POST /users`, `PUT /users/{id}` | `{"name","email","password","address","phone","preferences"}` | `name`, `email` (valid format), `password` (at least 8 characters) required; others optional |
| `POST /users/login` | `{"email","password"}` | both required, `email` valid format; no length rule, so older shorter passwords still sign in |

### Direct only (not routed by the gateway)

| Method | URL | Description |
|---|---|---|
| `GET` | `http://localhost:8081/ccar/kdb/health` | KDB connectivity and pool counts |
| `GET` | `http://localhost:8081/ccar/kdb/metrics` | Host, active/idle connections, waiting threads, utilization % |
| `GET` | `http://localhost:8081/ccar/swagger-ui.html` | OpenAPI / Swagger UI for ccar-core |
Calling the services directly skips JWT validation. Use the gateway for anything other than local debugging.

> `spring.h2.console.enabled=true` is set in user-service, but Spring Boot 4 serves `/h2-console` only when the
> `spring-boot-h2console` module is on the classpath, and it is not a dependency yet.

### Error codes per endpoint

| Endpoint | Possible errors |
|---|---|
| any protected path | `401 UNAUTHORIZED` / `TOKEN_EXPIRED` / `TOKEN_INVALID` |
| any path | `404 ROUTE_NOT_FOUND` (gateway), `503 DOWNSTREAM_UNAVAILABLE`, `504 DOWNSTREAM_TIMEOUT`, `503 CIRCUIT_OPEN`, `500 INTERNAL_ERROR` |
| `/users…` | `400 VALIDATION_FAILED` / `MALFORMED_REQUEST` / `INVALID_PARAMETER`, `401 INVALID_CREDENTIALS`, `404 USER_NOT_FOUND`, `409 DUPLICATE_EMAIL` / `CONFLICT`, `503 SERVICE_UNAVAILABLE` |
| `/ccar/forecast/forecast-ccar` | `503 KDB_UNAVAILABLE` / `KDB_POOL_EXHAUSTED`, `504 KDB_TIMEOUT`, `502 KDB_QUERY_FAILED` |
| `/ccar/forecast/bu-hierarchy…` | the KDB errors above, `400 INVALID_PARAMETER`, `404 BU_NOT_FOUND`, `503 BU_HIERARCHY_UNAVAILABLE`, `500 BU_HIERARCHY_INVALID` |

## Errors and tracing

- **One error format.** Every error from every service is `application/problem+json` with `type`, `title`,
  `status`, `detail`, `instance`, `errorCode`, `correlationId`, `service`, `timestamp` (and `errors` for
  validation). Details never include queries, hosts, SQL or stack traces.
- **Correlation id.** Send `X-Correlation-Id` (letters, digits, `.`, `_`, `:`, `-`, up to 128 chars) or let the
  gateway create one. It is forwarded to the service, written on every log line
  (`[correlationId]` in ccar-core and user-service, `[id]` prefix in the gateway), returned in the response
  header and put in the error body.
- **Logged once.** 5xx errors are logged at ERROR with the stack trace, 4xx as one WARN line, by the
  service's global exception handler.

Full details: [ERROR_HANDLING.md](ERROR_HANDLING.md).

## Configuration

**ccar-api-gateway** (`application.properties`)

| Property | Default | Meaning |
|---|---|---|
| `ccar.gateway.services.ccar-core.uri` | `http://localhost:8081` | ccar-core base URL |
| `ccar.gateway.services.ccar-core.response-timeout-ms` | `35000` | Response timeout for ccar-core |
| `ccar.gateway.services.user-service.uri` | `http://localhost:8082` | user-service base URL |
| `ccar.gateway.services.user-service.response-timeout-ms` | `10000` | Response timeout for user-service |
| `spring.cloud.gateway.httpclient.connect-timeout` | `2000` | Connect timeout (ms) |
| `ccar.gateway.circuit-breaker.*` | 50 % / 20 calls / 30 s open / 40 s limit | Circuit breaker defaults |
| `ccar.gateway.retry-after-seconds` | `30` | `Retry-After` on gateway 503s |
| `ccar.security.jwt.secret` | (base64) | Must match user-service |

**ccar-core**

| Property | Default | Meaning |
|---|---|---|
| `kdb.host`, `kdb.port`, `kdb.username`, `kdb.password` | – | KDB+ connection |
| `kdb.query-timeout-millis` | `30000` | Socket read timeout per query (→ 504 `KDB_TIMEOUT`) |
| `kdb.max-wait-millis` | `5000` | Wait for a free pooled connection (→ 503 `KDB_POOL_EXHAUSTED`) |
| `kdb.max-total` / `max-idle` / `min-idle` | `20` / `10` / `2` | Pool size |
| `ccar.bu-hierarchy.table` | `BU_HIERARCHY` | KDB table for the BU tree |
| `ccar.bu-hierarchy.retry-interval-ms` | `30000` | Fail-fast window after a failed hierarchy load |
| `ccar.errors.retry-after-seconds` | `30` | `Retry-After` on KDB 503s |
| `kdb.logging.slow-query-threshold-ms` | `1000` | Slow-query WARN threshold |

**ccar-user-service**

| Property | Default | Meaning |
|---|---|---|
| `ccar.security.jwt.secret` | (base64, ≥ 64 bytes) | HS512 signing key, must match the gateway |
| `ccar.security.jwt.expiration-seconds` | `3600` | Token lifetime |
| `spring.datasource.url` | `jdbc:h2:mem:userdb` | In-memory database |

## Known gaps

- The UI now signs in and goes through the gateway (:8089), but its data calls use paths such as
  `/ccar/bu/hierarchy` that are not routed by the gateway or implemented in ccar-core yet, so with
  `VITE_USE_MOCK=false` those screens show "endpoint not found" after signing in.
- Seeded users in `data.sql` cannot log in (SHA-256 hashes vs BCrypt); register new users instead.
- H2 is in memory: users are lost when user-service restarts.
- Single instances with fixed URLs; no service discovery or scaling.
- The JWT secret and KDB credentials are in `application.properties`; move them to environment variables or a
  vault before any shared deployment.
