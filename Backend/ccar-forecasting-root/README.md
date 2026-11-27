# CCAR-Forecasting-POC

Backend for the CCAR Forecasting POC: a multi-module Maven project with an API gateway, three Spring Boot
services (BU / GEO hierarchies from KDB+, users / login, and forecast / actuals / variance data from KDB+), a reusable KDB+ client starter and a shared
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
                                                      │  ReactiveGlobalExcHandler     │
                                                      └───────────────┬───────────────┘
                                                                      │
                 ┌────────────────────────────────┬───────────────────┴────────────┐
                 │ /ccar/forecast/** (35 s)       │ /ccar/v1/** (35 s)             │ /users/** (10 s)
                 ▼                                ▼                                ▼
  ┌─────────────────────────────┐  ┌─────────────────────────────┐  ┌─────────────────────────────┐
  │ ccar-hierarchy-service:8081 │  │ ccar-forecasting-service    │  │ ccar-user-service :8082     │
  │ HierarchyController         │  │   :8083, /ccar/v1           │  │ UserController              │
  │ HierarchyService (BU,GEO..) │  │ ForecastingController       │  │ UserServiceImpl             │
  │ HierarchyCache (per type)   │  │ ReferenceDataController     │  │   (BCrypt, JWT)             │
  │ (ccar-common exc. handler)  │  │ ForecastingService          │  │ (ccar-common exc. handler)  │
  │   └ kdb-client-starter      │  │ HierarchyRepository (cache) │  └──────────────┬──────────────┘
  │     (KdbTemplate + pool)    │  │ FactDataRepository          │                 │ JPA
  └──────────────┬──────────────┘  │   └ kdb-client-starter      │                 │
                 │                 └──────────────┬──────────────┘                 │
                 │   q IPC (30 s read timeout)    │                                ▼
                 ▼                                ▼                         ┌─────────────┐
          ┌────────────────────────────────────────────────────────────┐    │ H2 (memory) │
          │ KDB+ :5173   BU_HIERARCHY, GEO_HIERARCHY,                  │    └─────────────┘
          │              CCAR_FORECAST, CCAR_ACTUALS, CCAR_VARIANCE    │
          └────────────────────────────────────────────────────────────┘

  ccar-common (shared library, used by the gateway and all three services):
  ErrorCode catalogue · RFC 9457 problem+json · correlation-id filter · GlobalExceptionHandler · AOP logging
```

- Every production request enters through the **gateway on :8089**. It assigns or keeps the correlation id,
  validates the JWT (issued by user-service with the same HS512 secret) and routes to the service.
- ccar-hierarchy-service and ccar-forecasting-service read KDB+ only through the pooled `KdbTemplate` from
  `kdb-client-starter`.
- All services return errors in one format (`application/problem+json`) with an `errorCode` and the
  `correlationId`.

## Modules

| Module | Artifact | Type | Port | Spring Boot | Java |
|---|---|---|---|---|---|
| (root) | `com.ccar:ccar-forecasting-parent:1.0.0` | Parent / aggregator POM | – | 4.1.2 | 21 |
| [`ccar-common`](ccar-common) | `com.ccar:ccar-common:1.0.0` | Library (errors + tracing + logging) | – | 4.1.2 (optional deps) | 17 |
| [`kdb-client-starter`](kdb-client-starter) | `com.iris.kdb:kdb-starter:1.0.0` | Library (Spring Boot starter) | – | 4.1.2 | 17 |
| [`ccar-hierarchy-service`](ccar-hierarchy-service) | `com.example:ccar-hierarchy-service:0.0.1-SNAPSHOT` | Spring Boot application | 8081 | 4.1.2 | 21 |
| [`ccar-user-service`](ccar-user-service) | `com.ccar:ccar-user-service:1.0.0` | Spring Boot application | 8082 | 4.1.2 | 21 |
| [`ccar-forecasting-service`](ccar-forecasting-service) | `com.ccar:ccar-forecasting-service:1.0.0` | Spring Boot application | 8083 | 4.1.2 | 21 |
| [`ccar-api-gateway`](ccar-api-gateway) | `com.ccar:ccar-api-gateway:0.0.1-SNAPSHOT` | Spring Cloud Gateway application | 8089 | 3.4.5 | 21 |

Reactor build order: `kdb-client-starter` → `ccar-common` → `ccar-hierarchy-service` → `ccar-user-service` → `ccar-forecasting-service` → `ccar-api-gateway`.

> `ccar-api-gateway` is a module of the root POM but uses `spring-boot-starter-parent` **3.4.5** with Spring
> Cloud **2024.0.1**, because Spring Cloud Gateway 2024.0.x does not support Spring Boot 4.x. `ccar-common`
> declares all its Spring dependencies as optional, so it is safe on both Boot 3 and Boot 4.

### `ccar-common`

Shared contract for errors, tracing and logging.

- `error/ErrorCode`: the catalogue of error codes (status + title) used by every service and the UI.
- `error/ProblemDetailFactory`: builds the RFC 9457 problem body with `errorCode`, `correlationId`, `service`
  and `timestamp`.
- `error/CcarException`: base exception for application errors (`new CcarException(ErrorCode.X, "detail")`).
- `tracing/CorrelationId`: header name, MDC key and validation of incoming ids.
- `web/` (servlet apps, auto-configured): `CorrelationIdFilter`, `GlobalExceptionHandler` (the one
  `@RestControllerAdvice` of ccar-hierarchy-service and ccar-user-service; services declare none of their own),
  `CcarErrorAttributes` (same format for errors outside MVC), `MdcTaskDecorator`,
  `CorrelationIdClientInterceptor`. Store-specific mappings are `CcarErrorMapper` beans registered only when
  the library is on the service's classpath: `KdbErrorMapper` (kdb-starter → 503/504/502),
  `PersistenceErrorMapper` (Spring Data → 409/503), `ConstraintViolationErrorMapper` (Bean Validation → 400).
  A service adds its own mapping by declaring a `CcarErrorMapper` bean.
- `reactive/` (WebFlux apps, i.e. the gateway, auto-configured): `ReactiveGlobalExceptionHandler` (the
  gateway's global error handler), `ProblemResponseWriter`, `ReactiveErrorClassifier`.
- `logging/LoggingAspect` (all three services, auto-configured by `CcarLoggingAutoConfiguration`): entry/exit
  timing for `@RestController`, `@Controller` and `@Service` beans under `com.ccar..` / `com.forecast..`.
  INFO logs `<- method() completed in N ms`; DEBUG adds truncated args/results and failures (the exception
  handlers log each failure once). `Mono`/`Flux` results (gateway) are timed until they terminate. Active
  only when AspectJ is on the classpath (`spring-boot-starter-aspectj` on Boot 4, `spring-boot-starter-aop`
  on Boot 3); `ccar.logging.enabled=false` turns it off, `ccar.logging.max-length` (default 500) sets the
  truncation. The kdb-starter's `KdbLoggingAspect` stays separate.

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

### `ccar-hierarchy-service`

Hierarchies (BU, GEO, …) read from KDB+ and cached in memory. Port **8081**, context path **`/ccar`**.

> The old `GET /forecast/forecast-ccar` endpoint (`CcarForecastController` + `KdbService`) has been removed;
> that path now returns `404 RESOURCE_NOT_FOUND`. Forecast, actuals and variance data are served by
> `ccar-forecasting-service` under `/ccar/v1`.

- **Generic hierarchies** (BU, GEO and any future type), all served by the same code:
  - `controller/HierarchyController`: `/forecast/hierarchy/{type}/…`, where `{type}` is `BU`, `GEO`, …
    (case-insensitive).
  - `service/HierarchyService`: one cached tree per type. Types under `ccar.hierarchy.types.*` are loaded at
    startup. Any other type `XYZ` is served on its first request from the KDB table `XYZ_HIERARCHY`, if that
    table exists (404 `HIERARCHY_NOT_FOUND` otherwise) - no code or config change needed.
  - `service/HierarchyCache`: loads one hierarchy table once, builds and caches the tree. While KDB+ is down it
    answers 503 + `Retry-After` for `ccar.hierarchy.retry-interval-ms` instead of retrying on every request; a
    failed reload keeps the previous tree.
  - `model/HierarchyDefinition`: table and column names of a type. Every hierarchy table has the same layout,
    only the prefix differs: `<P>_ID, <P>_NAME, LEVEL_ID, <P>_ID_LEVEL1..n` (`BU_ID…`, `GEO_ID…`).
  - `model/HierarchyNode` (tree node: `id, name, levelId, parentId, childCount, children`) and
    `model/HierarchyInfo` (type status).
- Errors: ccar-common's shared `GlobalExceptionHandler`; its `KdbErrorMapper` maps KDB failures to
  503 / 504 / 502.
- Controller/service entry/exit timings come from ccar-common's shared `LoggingAspect` (DEBUG shows
  args/results).
- `client/KdbClient`: standalone `main()` for testing KDB connectivity by hand.

### `ccar-user-service`

Registration, profiles and login; issues the JWT the gateway validates. Port **8082**, H2 in-memory DB.

- `UserController` / `UserServiceImpl`: CRUD on users, `POST /users/login` returns a JWT (HS512, 1 h).
- Passwords are stored with BCrypt.
- Errors: ccar-common's shared `GlobalExceptionHandler`: 404 `USER_NOT_FOUND`, 409 `DUPLICATE_EMAIL`, 401
  `INVALID_CREDENTIALS`, 400 validation (every field listed), 409/503 for database errors
  (`PersistenceErrorMapper`).

### `ccar-forecasting-service`

The UI's data API: portfolios, T0 dates, the flat hierarchy, and forecast / actuals / variance totals for any
hierarchy node, read from KDB+. Port **8083**, context path **`/ccar`**, all endpoints under **`/ccar/v1`**
(gateway route `/ccar/v1/**`). Endpoints: [Forecasting data (/ccar/v1)](#forecasting-data-ccarv1).

- `controller/ReferenceDataController`: `GET /v1/portfolios`, `GET /v1/t0-dates`.
- `controller/ForecastingController`: `GET /v1/{forecast|actuals|variance}/aggregate-summary` and
  `.../detailed-breakdown`.
- `service/ForecastingService`: turns the flat KDB results into the UI JSON. Every configured account (Balance,
  Revenue, Expense) is always returned in configured order; a period without data is `null` (the UI shows "-").
  Paging of the breakdown is done on the cached children, so KDB+ is only asked for the page shown.
- `hierarchy/HierarchyRepository`: loads each configured hierarchy table (`ccar.forecasting.hierarchy.types.*`)
  once and caches it as an immutable snapshot (flat list + parent index). Warmed at startup; retried on the next
  request after a failure.
- `repository/FactDataRepository`: builds one q query per request. Aggregation happens in KDB+: each fact row
  is joined (`ej`) to the hierarchy table on its node column and grouped by its ancestor at the requested level
  (`<P>_ID_LEVELn`), so a node's total covers its whole subtree. Each period also returns its count of non-null
  values (`N__<col>`) because q's `sum` turns all-null into 0. The `c.Flip` result is read with
  `kdb/FlipReader`.
- `repository/ReferenceDataRepository`: `exec distinct` of portfolio ids and T0 dates from
  `ccar.forecasting.reference-table`.
- `kdb/QIdentifiers`: everything spliced into q text is a validated identifier from configuration, a number, a
  date, or a request value matching `[A-Za-z0-9_.-]{1,64}`, so request input cannot change a query.
- **Expected fact table layout** (`CCAR_FORECAST`, `CCAR_ACTUALS`, `CCAR_VARIANCE`; names configurable):

  | Column | q type | Meaning |
  |---|---|---|
  | `BU_ID`, `GEO_ID` | `j` | Node the row is booked against in each hierarchy (`fact-column` of the type) |
  | `ACCOUNT_ID` | `j` or `s` | `210001` Balance, `400001` Revenue, `500001` Expense |
  | `PORTFOLIO_ID` | `s` | Portfolio |
  | `T0_DATE` | `d` | Quarter-end reference date |
  | `T0`, `T_1` .. `T_36` | `j` or `f` | Forecast: T0, T+1 .. T+36. Actuals / variance: T0, T-1 .. T-36. Null = no data |

  [`CCAR_FACT_TABLES_LOAD.q`](CCAR_FACT_TABLES_LOAD.q) builds sample tables in this layout from `BU_HIERARCHY`.
- **Aggregate summary via KDB functions:** `aggregate-summary` calls q functions defined on the KDB server,
  `getAggregateActuals` and `getAggregateForecast` (`ccar.forecasting.datasets.{actuals,forecast}.aggregate-function`),
  as `fn[hierarchyType; hierarchyId; portfolioId; currencyCode; t0Date]`, e.g. ``getAggregateActuals[`BU;1;`ALL;`USD;2026.09.30]``.
  Arguments are sent as typed IPC values (`KdbTemplate.call`), not spliced into query text. Both functions must be
  loaded on the server (`kdb/detailed_actuals.q` defines `getAggregateActuals`). The detailed breakdown still builds
  its query in `FactDataRepository`.
  The variance aggregate summary returns the actuals and forecast tables side by side; the variance table is
  read only by the variance detailed breakdown.
- Errors, correlation id and controller/service logging come from ccar-common (`KdbErrorMapper` maps KDB
  failures to 503 / 504 / 502); the service has no handler or aspect of its own.

### `ccar-api-gateway`

Single entry point, built on Spring Cloud Gateway. Port **8089**.

- `logging/CorrelationIdWebFilter`: accepts or creates `X-Correlation-Id`, forwards it, echoes it on every
  response and logs one line in and one line out per request.
- `security/JwtAuthenticationFilter`: requires `Authorization: Bearer <JWT>` except on `POST /users` and
  `POST /users/login`. Returns 401 `UNAUTHORIZED`, `TOKEN_EXPIRED` or `TOKEN_INVALID` with a
  `WWW-Authenticate` header.
- Routes with a Resilience4j circuit breaker each (`config/ResilienceConfig`), fallback to
  `error/FallbackController` (503 / 504). Routes: `/ccar/forecast/**` → ccar-hierarchy-service,
  `/ccar/v1/**` → ccar-forecasting-service, `/users/**` → ccar-user-service. GETs to ccar-hierarchy-service and
  ccar-forecasting-service are retried only when the connection is refused.
- Errors: ccar-common's shared `ReactiveGlobalExceptionHandler`: unknown route 404, downstream 503/504,
  unexpected 500, all in the shared format.

## Ports at a glance

| Component | URL |
|---|---|
| API gateway (use this) | `http://localhost:8089` |
| ccar-hierarchy-service (direct) | `http://localhost:8081/ccar` |
| ccar-user-service (direct) | `http://localhost:8082` |
| ccar-forecasting-service (direct) | `http://localhost:8083/ccar` |
| KDB+ | `kdb.host`, `kdb.port` in the `application.properties` of ccar-hierarchy-service and ccar-forecasting-service |
| UI (Vite dev server) | `http://localhost:5173` |

## Prerequisites

- Java 21 (the two libraries target 17)
- Maven 3.8+, or the bundled wrapper `./ccar-hierarchy-service/mvnw`
- A running KDB+ instance with the `BU_HIERARCHY` and `GEO_HIERARCHY` tables, and for ccar-forecasting-service
  the `CCAR_FORECAST`, `CCAR_ACTUALS` and `CCAR_VARIANCE` tables
- `curl` (examples below use bash / Git Bash; PowerShell equivalents are given for the login step)

## Build and test

From this directory:

```bash
./ccar-hierarchy-service/mvnw clean install      # or: mvn clean install
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
q)\l BU_HIERARCHY_LOAD.q          / BU_HIERARCHY from BU_HIERARCHY1.csv (see the header of the script)
q)/ GEO_HIERARCHY: same layout with the GEO_ prefix
q)/   sample data: ccar-hierarchy-service/src/main/resources/GEO_HIERARCHY_FLAT.csv
```

`BU_HIERARCHY_INSERT_100.q` loads only the first 100 rows, useful for a quick test.

Sample forecast / actuals / variance tables for ccar-forecasting-service (built from `BU_HIERARCHY`, and from
`GEO_HIERARCHY` when it exists; 4 T0 dates × 2 portfolios × 3 accounts per leaf BU):

```
q)\l CCAR_FACT_TABLES_LOAD.q
q)build[]                                             / in the KDB+ server's own session
q)/ or, from a local q session: pullBuildPush[`:192.168.11.200:5173:test:test@123]
```

**2. ccar-hierarchy-service**

```bash
cd ccar-hierarchy-service && ./mvnw spring-boot:run
```

On startup it loads every configured hierarchy (BU, GEO) from KDB+ and logs `BU hierarchy loaded: N node(s) ...`. If KDB+ is not
reachable it logs the error and keeps running; hierarchy requests then return 503 until KDB+ is back.

**3. ccar-user-service**

```bash
cd ccar-user-service && ../ccar-hierarchy-service/mvnw spring-boot:run
```

**4. ccar-forecasting-service**

```bash
cd ccar-forecasting-service && ../ccar-hierarchy-service/mvnw spring-boot:run
```

On startup it loads the BU and GEO hierarchies and logs `BU hierarchy loaded from BU_HIERARCHY: N node(s) ...`.
A hierarchy that fails to load (KDB+ down, table missing) is logged and retried on its next request.

**5. ccar-api-gateway**

```bash
cd ccar-api-gateway && ./mvnw spring-boot:run
```

**6. UI (optional)**

```bash
cd ../../ccar_ui && npm install && npm run dev
```

Logs are written to `logs/ccar.log` (ccar-hierarchy-service), `logs/ccar-api-gateway.log` (gateway),
`logs/ccar-forecasting-service.log` (forecasting-service) and the console (user-service). Every line carries the correlation id.

---

## Step-by-step API walkthrough (login first)

All calls go through the gateway on **:8089**. Only registration and login are public; everything else needs
the JWT from step 2. The forecast / actuals / variance screens use
[Forecasting data (/ccar/v1)](#forecasting-data-ccarv1).

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

### Step 4 – Get the top of a hierarchy (BU, GEO, …)

```bash
# which hierarchies exist, and are they loaded?
curl -s http://localhost:8089/ccar/forecast/hierarchy -H "Authorization: Bearer $TOKEN"

curl -s "http://localhost:8089/ccar/forecast/hierarchy/BU?depth=1" -H "Authorization: Bearer $TOKEN"
curl -s "http://localhost:8089/ccar/forecast/hierarchy/GEO?depth=1" -H "Authorization: Bearer $TOKEN"
```

The `{type}` path segment selects the hierarchy; every endpoint below works the same for any type.
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

### Step 5 – Open one node (node plus direct children)

```bash
curl -s "http://localhost:8089/ccar/forecast/hierarchy/BU/2?depth=1" \
  -H "Authorization: Bearer $TOKEN"
```

### Step 6 – Lazy-load the children of a node (for expanding a tree node)

```bash
curl -s http://localhost:8089/ccar/forecast/hierarchy/GEO/2/children \
  -H "Authorization: Bearer $TOKEN"
```

Returns a JSON array of nodes without grandchildren. An unknown id returns `404` with
`"errorCode": "HIERARCHY_NODE_NOT_FOUND"`; an unknown type returns `404 HIERARCHY_NOT_FOUND`.

### Step 7 – Reload a hierarchy after its KDB table changed

```bash
curl -s -X POST http://localhost:8089/ccar/forecast/hierarchy/GEO/reload \
  -H "Authorization: Bearer $TOKEN"
```

```json
{ "type": "GEO", "table": "GEO_HIERARCHY", "loaded": true, "nodes": 2916, "loadedAt": "2026-11-11T10:42:07.118Z" }
```

### Step 8 – Manage users

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

### Step 9 – See the error format (optional)

```bash
# no token -> 401 UNAUTHORIZED
curl -i http://localhost:8089/ccar/forecast/hierarchy/BU

# unknown node -> 404 HIERARCHY_NODE_NOT_FOUND
curl -s http://localhost:8089/ccar/forecast/hierarchy/BU/999999 -H "Authorization: Bearer $TOKEN"
```

Example `401` body (`Content-Type: application/problem+json`, header `WWW-Authenticate: Bearer realm="ccar"`):

```json
{
  "type": "urn:ccar:error:unauthorized",
  "title": "Authentication required",
  "status": 401,
  "detail": "A Bearer access token is required.",
  "instance": "/ccar/forecast/hierarchy/BU",
  "errorCode": "UNAUTHORIZED",
  "correlationId": "0b6f5a4e-2c1d-4b8e-9a43-7d2f1e6c9b10",
  "service": "ccar-api-gateway",
  "timestamp": "2026-11-11T10:45:00.123Z"
}
```

If KDB+ is down when a hierarchy is loaded or reloaded, steps 4-7 return `503` (`KDB_UNAVAILABLE`, or
`HIERARCHY_UNAVAILABLE` during the retry window) with `Retry-After: 30`. Once loaded, a hierarchy keeps being
served from the cache while KDB+ is down. Search
the logs for the `correlationId` to see the full trail across services.

### Forecasting data (/ccar/v1)

ccar-forecasting-service through the gateway, with the token from step 3. Needs the `CCAR_FORECAST`,
`CCAR_ACTUALS` and `CCAR_VARIANCE` tables (see [Run locally](#run-locally)).

```bash
# filters for the screen
curl -s http://localhost:8089/ccar/v1/portfolios -H "Authorization: Bearer $TOKEN"
# [{"id":"ALL","name":"All Portfolios"},{"id":"PORT_0001","name":"PORT_0001"}, ...]

curl -s http://localhost:8089/ccar/v1/t0-dates -H "Authorization: Bearer $TOKEN"
# ["2026-09-30","2026-06-30","2026-03-31","2025-12-31"]          latest first


# totals of one node; replace forecast with actuals or variance (periods then run T0, T-1 .. T-36)
curl -s "http://localhost:8089/ccar/v1/forecast/aggregate-summary?hierarchyType=BU&hierarchyId=1&portfolioId=ALL&t0Date=2026-09-30" \
  -H "Authorization: Bearer $TOKEN"
```

```json
{
  "scope": { "type": "BU", "id": 1, "name": "ALL_BANK", "level": 1 },
  "t0Date": "2026-09-30",
  "portfolioId": "ALL",
  "currency": "USD",
  "periods": ["T0", "T+1", "T+2", "...", "T+36"],
  "periodValuesForecast": [
    { "accountId": "210001", "measure": "Balance", "values": [8504978, 8516417, 8531029, "...", 9683892] },
    { "accountId": "400001", "measure": "Revenue", "values": [79214, 79802, null, "...", 96925] },
    { "accountId": "500001", "measure": "Expense", "values": [730235, 734120, 738011, "...", 868221] }
  ]
}
```

The value list depends on the data set: forecast returns `periodValuesForecast`, actuals returns
`periodValuesActual`, and variance returns both (actuals from the actuals table, forecast from the forecast
table, same node, T0 date and portfolio), so the UI can compare them period by period.
`null` means no data for that period; the UI shows `-`. `values[i]` belongs to `periods[i]`.

```bash
# direct children of the node, paged (page 0-based, default 0; size default 25, max 500)
curl -s "http://localhost:8089/ccar/v1/forecast/detailed-breakdown?hierarchyType=BU&hierarchyId=1&portfolioId=ALL&t0Date=2026-09-30&page=0&size=25" \
  -H "Authorization: Bearer $TOKEN"
```

```json
{
  "scope": { "type": "BU", "id": 1, "name": "ALL_BANK", "level": 1 },
  "t0Date": "2026-09-30", "portfolioId": "ALL", "currency": "USD",
  "periods": ["T0", "T+1", "...", "T+36"],
  "page": 0, "size": 25, "totalElements": 5, "totalPages": 1,
  "items": [
    { "id": 2, "name": "North America", "hType": "BU", "level": 2, "childCount": 4,
      "data_rows": [
        { "accountId": "210001", "measure": "Balance", "values": [3401991, "..."] },
        { "accountId": "400001", "measure": "Revenue", "values": [31685, "..."] },
        { "accountId": "500001", "measure": "Expense", "values": [292094, "..."] }
      ] }
  ]
}
```

Parameters: `hierarchyType` (`BU` | `GEO`, case-insensitive), `hierarchyId`, `t0Date` (`yyyy-MM-dd`) are required;
`portfolioId` defaults to `ALL`. Swagger UI: `http://localhost:8083/ccar/swagger-ui.html`.

### Postman

All of the above, plus about 60 negative and tracing cases (94 requests) with automated checks, is in
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
| `GET` | `/ccar/forecast/hierarchy` | Bearer | Hierarchy types with table and load status | `200` array |
| `GET` | `/ccar/forecast/hierarchy/{type}?depth={n}` | Bearer | Tree of `BU`, `GEO`, … from the root (`depth` optional, ≥ 0) | `200` node |
| `GET` | `/ccar/forecast/hierarchy/{type}/{nodeId}?depth={n}` | Bearer | Subtree of one node (`depth` default 1) | `200` node |
| `GET` | `/ccar/forecast/hierarchy/{type}/{nodeId}/children` | Bearer | Direct children of one node | `200` array |
| `POST` | `/ccar/forecast/hierarchy/{type}/reload` | Bearer | Reload that hierarchy from KDB+ | `200 {type, table, loaded, nodes, loadedAt}` |
| `GET` | `/ccar/v1/portfolios` | Bearer | Portfolios, `ALL` first | `200 [{id, name}]` |
| `GET` | `/ccar/v1/t0-dates` | Bearer | T0 dates with data, latest first | `200 ["2026-09-30", …]` |
| `GET` | `/ccar/v1/hierarchy/root/{type}` | Bearer | Whole `BU` / `GEO` hierarchy, flat, root first | `200 [{id, name, level, parentId, childCount, isLeafNode}]` |
| `POST` | `/ccar/v1/hierarchy/{type}/reload` | Bearer | Reload ccar-forecasting-service's cached hierarchy | `200 {type, nodes}` |
| `GET` | `/ccar/v1/{forecast\|actuals\|variance}/aggregate-summary?hierarchyType&hierarchyId&portfolioId&t0Date` | Bearer | Totals of one node per account and period | `200 {scope, t0Date, portfolioId, currency, periods, periodValuesActual and/or periodValuesForecast}` |
| `GET` | `/ccar/v1/{forecast\|actuals\|variance}/detailed-breakdown?…&page&size` | Bearer | Same for a page of the node's direct children | `200 {scope, …, page, size, totalElements, totalPages, items[{id, name, hType, level, childCount, data_rows}]}` |

`GET /ccar/v1/hierarchy/child/{type}` (lazy loading) is a planned enhancement; for now `root/{type}` returns the
whole hierarchy.

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
| `GET` | `http://localhost:8081/ccar/swagger-ui.html` | OpenAPI / Swagger UI for ccar-hierarchy-service |
| `GET` | `http://localhost:8083/ccar/kdb/health` | KDB connectivity of ccar-forecasting-service |
| `GET` | `http://localhost:8083/ccar/kdb/metrics` | KDB pool metrics of ccar-forecasting-service |
| `GET` | `http://localhost:8083/ccar/swagger-ui.html` | OpenAPI / Swagger UI for ccar-forecasting-service |

Calling the services directly skips JWT validation. Use the gateway for anything other than local debugging.

> `spring.h2.console.enabled=true` is set in user-service, but Spring Boot 4 serves `/h2-console` only when the
> `spring-boot-h2console` module is on the classpath, and it is not a dependency yet.

### Error codes per endpoint

| Endpoint | Possible errors |
|---|---|
| any protected path | `401 UNAUTHORIZED` / `TOKEN_EXPIRED` / `TOKEN_INVALID` |
| any path | `404 ROUTE_NOT_FOUND` (gateway), `503 DOWNSTREAM_UNAVAILABLE`, `504 DOWNSTREAM_TIMEOUT`, `503 CIRCUIT_OPEN`, `500 INTERNAL_ERROR` |
| `/users…` | `400 VALIDATION_FAILED` / `MALFORMED_REQUEST` / `INVALID_PARAMETER`, `401 INVALID_CREDENTIALS`, `404 USER_NOT_FOUND`, `409 DUPLICATE_EMAIL` / `CONFLICT`, `503 SERVICE_UNAVAILABLE` |
| other `/ccar/forecast/…` paths (incl. the removed `forecast-ccar`) | `404 RESOURCE_NOT_FOUND` |
| `/ccar/v1/…` | `400 INVALID_PARAMETER` (bad or missing `hierarchyType`, `hierarchyId`, `t0Date`, `portfolioId`, `page`, `size`), `404 HIERARCHY_NOT_FOUND` (type not configured), `404 HIERARCHY_NODE_NOT_FOUND`, `404 RESOURCE_NOT_FOUND` (unknown data set or path), `500 HIERARCHY_INVALID`, `503 KDB_UNAVAILABLE` / `KDB_POOL_EXHAUSTED`, `504 KDB_TIMEOUT`, `502 KDB_QUERY_FAILED` (q error, missing table, unexpected table layout) |
| `/ccar/forecast/hierarchy…` | `503 KDB_UNAVAILABLE` / `KDB_POOL_EXHAUSTED`, `504 KDB_TIMEOUT`, `502 KDB_QUERY_FAILED` (load / reload), `400 INVALID_PARAMETER` (bad `type`, `depth` or id), `404 HIERARCHY_NOT_FOUND` (unknown type), `404 HIERARCHY_NODE_NOT_FOUND`, `503 HIERARCHY_UNAVAILABLE`, `500 HIERARCHY_INVALID` |

## Errors and tracing

- **One error format.** Every error from every service is `application/problem+json` with `type`, `title`,
  `status`, `detail`, `instance`, `errorCode`, `correlationId`, `service`, `timestamp` (and `errors` for
  validation). Details never include queries, hosts, SQL or stack traces.
- **Correlation id.** Send `X-Correlation-Id` (letters, digits, `.`, `_`, `:`, `-`, up to 128 chars) or let the
  gateway create one. It is forwarded to the service, written on every log line
  (`[correlationId]` in ccar-hierarchy-service, user-service and forecasting-service, `[id]` prefix in the gateway), returned in the response
  header and put in the error body.
- **Logged once.** 5xx errors are logged at ERROR with the stack trace, 4xx as one WARN line, by the
  service's global exception handler.

Full details: [ERROR_HANDLING.md](ERROR_HANDLING.md).

## Configuration

**ccar-api-gateway** (`application.properties`)

| Property | Default | Meaning |
|---|---|---|
| `ccar.gateway.services.ccar-hierarchy-service.uri` | `http://localhost:8081` | ccar-hierarchy-service base URL |
| `ccar.gateway.services.ccar-hierarchy-service.response-timeout-ms` | `35000` | Response timeout for ccar-hierarchy-service |
| `ccar.gateway.services.ccar-forecasting-service.uri` | `http://localhost:8083` | ccar-forecasting-service base URL |
| `ccar.gateway.services.ccar-forecasting-service.response-timeout-ms` | `35000` | Response timeout for ccar-forecasting-service |
| `ccar.gateway.services.user-service.uri` | `http://localhost:8082` | user-service base URL |
| `ccar.gateway.services.user-service.response-timeout-ms` | `10000` | Response timeout for user-service |
| `spring.cloud.gateway.httpclient.connect-timeout` | `2000` | Connect timeout (ms) |
| `ccar.gateway.circuit-breaker.*` | 50 % / 20 calls / 30 s open / 40 s limit | Circuit breaker defaults |
| `ccar.errors.retry-after-seconds` | `30` | `Retry-After` on gateway 503s |
| `ccar.security.jwt.secret` | (base64) | Must match user-service |

**ccar-hierarchy-service**

| Property | Default | Meaning |
|---|---|---|
| `kdb.host`, `kdb.port`, `kdb.username`, `kdb.password` | – | KDB+ connection |
| `kdb.query-timeout-millis` | `30000` | Socket read timeout per query (→ 504 `KDB_TIMEOUT`) |
| `kdb.max-wait-millis` | `5000` | Wait for a free pooled connection (→ 503 `KDB_POOL_EXHAUSTED`) |
| `kdb.max-total` / `max-idle` / `min-idle` | `20` / `10` / `2` | Pool size |
| `ccar.hierarchy.types.<TYPE>.table` | `<TYPE>_HIERARCHY` | KDB table of a hierarchy type (`BU` and `GEO` are configured) |
| `ccar.hierarchy.types.<TYPE>.column-prefix` | `<TYPE>` | Column prefix: `<P>_ID`, `<P>_NAME`, `<P>_ID_LEVELn` |
| `ccar.hierarchy.allow-unconfigured-types` | `true` | Serve other types from `<TYPE>_HIERARCHY` when that table exists |
| `ccar.hierarchy.retry-interval-ms` | `30000` | Fail-fast window after a failed hierarchy load |
| `ccar.errors.retry-after-seconds` | `30` | `Retry-After` on KDB 503s |
| `kdb.logging.slow-query-threshold-ms` | `1000` | Slow-query WARN threshold |

**ccar-forecasting-service** (`kdb.*` as for ccar-hierarchy-service)

| Property | Default | Meaning |
|---|---|---|
| `ccar.forecasting.datasets.{forecast,actuals,variance}.table` | `CCAR_FORECAST`, `CCAR_ACTUALS`, `CCAR_VARIANCE` | Fact table of each data set |
| `ccar.forecasting.reference-table` | `CCAR_FORECAST` | Source of portfolios and T0 dates |
| `ccar.forecasting.fact.account-column` / `portfolio-column` / `t0-date-column` | `ACCOUNT_ID` / `PORTFOLIO_ID` / `T0_DATE` | Fact table columns |
| `ccar.forecasting.fact.t0-column` / `period-column-prefix` | `T0` / `T_` | Period columns: `T0`, `T_1` .. `T_<period-count>` |
| `ccar.forecasting.period-count` | `36` | Periods after T0 |
| `ccar.forecasting.accounts[i].id` / `.measure` | `210001` Balance, `400001` Revenue, `500001` Expense | Measures, in display order (others found in the data are appended) |
| `ccar.forecasting.hierarchy.types.<TYPE>.table` | `<TYPE>_HIERARCHY` | Hierarchy table (`BU` and `GEO` are configured; other types → 404) |
| `ccar.forecasting.hierarchy.types.<TYPE>.column-prefix` | `<TYPE>` | `<P>_ID`, `<P>_NAME`, `<P>_ID_LEVELn` |
| `ccar.forecasting.hierarchy.types.<TYPE>.fact-column` | `<P>_ID` | Fact column holding the node id (`BU_ID`, `GEO_ID`) |
| `ccar.forecasting.all-portfolios-id` | `ALL` | Portfolio id meaning "no filter" |
| `ccar.forecasting.currency` | `USD` | Currency returned with the data (no FX conversion is done) |
| `ccar.forecasting.default-page-size` / `max-page-size` | `25` / `500` | Breakdown paging |

**ccar-user-service**

| Property | Default | Meaning |
|---|---|---|
| `ccar.security.jwt.secret` | (base64, ≥ 64 bytes) | HS512 signing key, must match the gateway |
| `ccar.security.jwt.expiration-seconds` | `3600` | Token lifetime |
| `spring.datasource.url` | `jdbc:h2:mem:userdb` | In-memory database |

## Known gaps

- The UI's data calls still use the older proposed paths (`/ccar/bu/hierarchy`, `/ccar/forecast`, …); they need
  to move to the `/ccar/v1/…` endpoints of ccar-forecasting-service.
- The fact tables `CCAR_FORECAST`, `CCAR_ACTUALS` and `CCAR_VARIANCE` do not exist in the shared KDB+ yet
  (only sample data from `CCAR_FACT_TABLES_LOAD.q`), and `GEO_HIERARCHY` is not loaded there either. The existing
  `forecast_ccar` table has no BU/GEO column, so it cannot be aggregated by hierarchy.
- Values are summed as stored and labelled with `ccar.forecasting.currency`; there is no FX conversion.
- No variance figures (actual minus forecast) are computed: the variance aggregate summary returns both series,
  and the variance detailed breakdown still reads `CCAR_VARIANCE`.
- Portfolio names are their ids (there is no portfolio reference table).
- Seeded users in `data.sql` cannot log in (SHA-256 hashes vs BCrypt); register new users instead.
- H2 is in memory: users are lost when user-service restarts.
- Single instances with fixed URLs; no service discovery or scaling.
- The JWT secret and KDB credentials are in `application.properties`; move them to environment variables or a
  vault before any shared deployment.
