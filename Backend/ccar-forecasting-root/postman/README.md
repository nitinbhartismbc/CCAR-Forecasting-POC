# Postman collection

| File | Purpose |
|---|---|
| `CCAR-Forecasting.postman_collection.json` | 94 requests with tests: happy path, negative cases, tracing, resilience |
| `CCAR-Local.postman_environment.json` | `baseUrl` (gateway :8089), `coreDirectUrl` (:8081/ccar), `forecastingDirectUrl` (:8083/ccar), `password`, `jwtSecret` |

## Use it

1. Postman → **Import** both files, then select the **CCAR Local** environment.
2. Start KDB+, ccar-hierarchy-service, ccar-user-service, ccar-forecasting-service and ccar-api-gateway (see the main README).
3. Open the collection → **Run** → keep the folder order → **Run CCAR Forecasting API**.

Command line (optional): `npx newman run CCAR-Forecasting.postman_collection.json -e CCAR-Local.postman_environment.json`

## What runs

| Folder | Covers |
|---|---|
| 01 Auth - happy path | Register a fresh user (unique email per run), log in, store the JWT in `{{token}}` |
| 02 Users - happy path | List, get, update |
| 03 Hierarchies (BU, GEO) - happy path | List types, BU and GEO trees (depth 0/1/full), node, children, case-insensitive type, reload BU and GEO. Stores `{{buId}}`, `{{geoId}}` |
| 04 Correlation id / tracing | Caller id echoed, unsafe id replaced, id in error body |
| 05 Negative - authentication | No token, non-Bearer, garbage, wrong signature, expired token, bad login, login validation |
| 06 Negative - users | Duplicate email (create and update), validation, password under 8 characters, malformed JSON, 415, 404, 400, 405 |
| 07 Negative - hierarchies | Unknown/malformed type, unknown BU and GEO node, bad id/depth, 405, removed `forecast-ccar` endpoint now 404, unknown path (ccar-hierarchy-service 404 vs gateway 404) |
| 08 Resilience | KDB+ / ccar-hierarchy-service / ccar-forecasting-service / user-service down → 503/504. **Tests are skipped while everything is healthy**; stop the dependency named in the request to exercise them |
| 09 Direct service endpoints | `/kdb/health`, `/kdb/metrics` on :8081, `/kdb/health` on :8083, and proof they are not exposed on the gateway |
| 10 Forecasting data (/ccar/v1) - happy path | ccar-forecasting-service through the gateway: portfolios (`ALL` first), T0 dates (latest first), flat BU and GEO hierarchy (`isLeafNode`, `childCount`), reload, aggregate summary and detailed breakdown for forecast / actuals / variance (period labels `T+n` / `T-n`, one value or `null` per period, Balance / Revenue / Expense first), one portfolio + child node, paging. Stores `{{t0Date}}`, `{{portfolioId}}`, `{{v1RootBuId}}`, `{{v1ChildBuId}}` |
| 10b Negative - forecasting data | Unsupported / malformed hierarchy type, unknown node, non-numeric id, missing or badly formatted `t0Date`, unsafe `portfolioId`, bad `page` / `size`, unknown data set, 405, no token |
| 11 Cleanup | Delete the users created by the run, then confirm 404 / 401 |

Every response is also checked at collection level: `X-Correlation-Id` is returned, and every 4xx/5xx is
`problem+json` with `errorCode`, `correlationId` (equal to the header), `service`, `timestamp`, a body
`status` equal to the HTTP status, and no stack trace or query text in `detail`.

## Notes

- Folder 03 needs the `BU_HIERARCHY` and `GEO_HIERARCHY` tables in KDB+; without them those requests fail
  with 503/502 (which is itself the correct error handling).
- `GET /ccar/forecast/forecast-ccar` was removed from ccar-hierarchy-service; folder 07 checks that it now returns 404.
- Folders 10 and 10b need the `CCAR_FORECAST`, `CCAR_ACTUALS` and `CCAR_VARIANCE` tables in KDB+ (sample data:
  `CCAR_FACT_TABLES_LOAD.q` in the repo root). The GEO request in folder 10 also needs `GEO_HIERARCHY`.
  10b uses `{{t0Date}}` and `{{v1RootBuId}}` stored by folder 10, so run 10 first.
- The expired / wrong-signature tokens are built in a pre-request script with `jwtSecret`. If you change
  `ccar.security.jwt.secret`, update the environment value too.
- The environment holds the local dev JWT secret that is already in `application.properties`. Don't reuse
  it outside local development.
