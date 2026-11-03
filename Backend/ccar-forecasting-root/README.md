# CCAR-Forecasting-POC

Backend for the CCAR Forecasting POC. It is a multi-module Maven project: an API gateway, a Spring Boot service that reads forecast data from KDB+, and a reusable KDB+ client starter.

## Architecture

```
  UI / client
      │   http://localhost:8089/ccar/forecast/**
      ▼
┌──────────────────────┐   X-Correlation-Id   ┌────────────────────────────┐      IPC       ┌───────────┐
│  ccar-api-gateway    │ ───────────────────▶ │  ccar-core  (port 8081)    │ ─────────────▶ │  KDB+     │
│  (port 8089)         │                      │  context-path /ccar        │                │  (5001)   │
└──────────────────────┘                      └────────────────────────────┘                └───────────┘
                                                        │ uses
                                                        ▼
                                              ┌────────────────────────────┐
                                              │  kdb-client-starter        │
                                              │  (pool, KdbTemplate, AOP)  │
                                              └────────────────────────────┘
```

## Modules

| Module | Artifact | Type | Port | Spring Boot | Java |
|---|---|---|---|---|---|
| (root) | `com.ccar:ccar-forecasting-parent:1.0.0` | Parent / aggregator POM | – | 4.1.2 | 21 |
| [`kdb-client-starter`](kdb-client-starter) | `com.iris.kdb:kdb-starter:1.0.0` | Library (Spring Boot starter) | – | 4.1.2 | 17 |
| [`ccar-core`](ccar-core) | `com.example:ccar-core:0.0.1-SNAPSHOT` | Spring Boot application | 8081 | 4.1.2 | 21 |
| [`ccar-api-gateway`](ccar-api-gateway) | `com.ccar:ccar-api-gateway:0.0.1-SNAPSHOT` | Spring Cloud Gateway application | 8089 | 3.4.5 | 21 |

The reactor builds them in this order: `kdb-client-starter` → `ccar-core` → `ccar-api-gateway`.

### Root POM (`ccar-forecasting-parent`)

The parent and aggregator for all modules. It inherits `spring-boot-starter-parent` 4.1.2 and pins the versions of the third-party libraries Spring Boot does not manage:

| Library | Version |
|---|---|
| `com.iris.kdb:kdb-starter` | 1.0.0 |
| `com.opencsv:opencsv` | 5.9 |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` | 3.0.3 |
| `org.apache.commons:commons-pool2` | 2.12.1 |
| `com.kx:javakdb` | 2.2 |

### `kdb-client-starter`

A reusable Spring Boot starter for KDB+ access. Any Spring Boot app picks it up by adding it as a dependency. It provides:

- **Auto-configuration** (`KdbAutoConfiguration`, `KdbProperties`): binds `kdb.*` properties and registers the pool, template and metrics controller beans.
- **Connection pooling** (`KdbConnectionPool`, `KdbConnectionFactory`): pools KDB+ IPC connections with Apache Commons Pool2.
- **Query execution** (`KdbTemplate`): runs q queries from one central place, plus `ping()` for health checks.
- **Monitoring** (`KdbMetricsController`, `KdbPoolMetrics`, `KdbPoolStats`):
  - `GET /kdb/health`: connectivity status and pool counts
  - `GET /kdb/metrics`: host, active/idle connections, waiting threads, utilization %
- **Query logging** (`KdbLoggingAspect`, `KdbLoggingAutoConfiguration`): an optional AOP aspect, active when AspectJ is on the classpath. It logs query text and duration, and logs slow queries at WARN.
- **Exceptions**: `KdbException` is the base class for `KdbConnectionException`, `KdbPoolExhaustedException` and `KdbQueryException`.

It compiles to Java 17 bytecode so Java 17 applications can use it too.

Key properties (defaults shown):

```properties
kdb.host=localhost
kdb.port=5001
kdb.username=
kdb.password=
kdb.max-total=20
kdb.max-idle=10
kdb.min-idle=2
kdb.max-wait-millis=5000
kdb.test-on-borrow=true
kdb.test-while-idle=true
kdb.time-between-eviction-runs-millis=30000
kdb.min-evictable-idle-time-millis=60000
kdb.logging.enabled=true
kdb.logging.slow-query-threshold-ms=1000
kdb.logging.max-query-length=500
```

See [`kdb-client-starter/README.md`](kdb-client-starter/README.md) for full usage.

### `ccar-core`

The main CCAR forecasting REST service (`CcarApplication`). It runs on port **8081** with context path **`/ccar`**.

| Endpoint | Description | Backed by |
|---|---|---|
| `GET /ccar/forecast/forecast-ccar` | Returns the KDB+ `forecast_ccar` table as JSON (`{"forecast_ccar": {"data": [...]}}`) | `KdbService` |
| `GET /ccar/forecast/bu-hierarchy` | Builds the Business Unit tree from `BU_HIERARCHY1.csv` and returns it as nested JSON | `BuHierarchyService` |
| `GET /ccar/kdb/health`, `GET /ccar/kdb/metrics` | KDB pool health and metrics (from the starter) | `KdbMetricsController` |
| `/ccar/swagger-ui.html` | OpenAPI / Swagger UI | springdoc |

Package layout (`com.forecast.ccar`):

- `controller/CcarForecastController`: REST endpoints under `/forecast`.
- `service/KdbService`: connects to KDB+, runs `select from forecast_ccar`, and converts the `c.Flip` result into row maps and then JSON.
- `service/BuHierarchyService`: reads the BU CSV (`BU_ID, BU_NAME, LEVEL_ID, parent columns…`) with OpenCSV and builds a `BuNode` tree.
- `model/BuNode`, `model/BuRow`: hierarchy models.
- `logging/CorrelationIdFilter`: reads `X-Correlation-Id` from the request or generates one, puts it in the MDC so every log line includes it, and echoes it on the response.
- `logging/LoggingAspect`: AOP method entry/exit logging. Set the log level to DEBUG to see arguments and results.
- `client/KdbClient`: a standalone `main()` for testing KDB connectivity by hand.

Resources:

- `application.properties`: port, context path, KDB connection (`kdb.host`, `kdb.port`, `kdb.username`, `kdb.password`), and logging to `logs/ccar.log`.
- `KDB_Tables.txt`: q script that creates a sample `forecast_ccar` table (`OP_CODE_ID`, `ACCOUNT_ID`, `CCY_CODE`, `PORTFOLIO_ID`, `T0_DATE`, `T0`, `T_1` … `T_36`).
- `BU_HIERARCHY1.csv` (module root): sample BU hierarchy. The path is relative, so start the service from the `ccar-core` directory.

### `ccar-api-gateway`

The single entry point for the UI, built on Spring Cloud Gateway (`CcarApiGatewayApplication`). It runs on port **8089**.

- **Routing**: route `ccar` forwards `Path=/ccar/forecast/**` to `http://localhost:8081`.
- **Request logging** (`logging/RequestLoggingFilter`): a global filter with highest precedence. It assigns or propagates `X-Correlation-Id` to downstream services and logs method, path, route, status and latency to `logs/ccar-api-gateway.log`.

> It is listed as a module in the root POM but uses `spring-boot-starter-parent` **3.4.5** with Spring Cloud **2024.0.1** as its parent, because Spring Cloud Gateway 2024.0.x does not support Spring Boot 4.x.

## Prerequisites

- Java 21 (the starter itself targets 17)
- Maven 3.8+ (each application module also ships `mvnw`)
- A running KDB+ instance reachable at the host and port in `ccar-core/src/main/resources/application.properties`
- The KDB Java API (`com.kx:javakdb`) available to Maven

## Build

From this directory:

```bash
mvn clean install
```

## Run

1. Start KDB+ and load the sample table:
   ```
   q -p 5001
   q) / paste the contents of ccar-core/src/main/resources/KDB_Tables.txt
   ```
2. Start `ccar-core`:
   ```bash
   cd ccar-core && ./mvnw spring-boot:run
   ```
3. Start the gateway:
   ```bash
   cd ccar-api-gateway && ./mvnw spring-boot:run
   ```
4. Call through the gateway:
   ```bash
   curl http://localhost:8089/ccar/forecast/forecast-ccar
   curl http://localhost:8089/ccar/forecast/bu-hierarchy
   ```

## Request tracing

Every request carries an `X-Correlation-Id` header. The gateway creates it or passes through the one it receives, `ccar-core` adds it to the MDC, and both services include it in their log lines and in the response header. To trace one request across services, grep both log files for that ID.
