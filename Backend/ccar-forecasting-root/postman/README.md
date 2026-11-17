# Postman collection

| File | Purpose |
|---|---|
| `CCAR-Forecasting.postman_collection.json` | 74 requests with tests: happy path, negative cases, tracing, resilience |
| `CCAR-Local.postman_environment.json` | `baseUrl` (gateway :8089), `coreDirectUrl` (:8081/ccar), `forecastingDirectUrl` (:8083), `password`, `jwtSecret` |

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
| 08 Resilience | KDB+ / ccar-hierarchy-service / user-service down → 503/504. **Tests are skipped while everything is healthy**; stop the dependency named in the request to exercise them |
| 09 Direct service endpoints | `/kdb/health`, `/kdb/metrics` on :8081, and proof they are not exposed on the gateway |
| 10 Forecasting service - direct | ccar-forecasting-service on :8083 (no gateway route yet): create (201 + Location), list, filter by scenario, get, validation 400, bad scenario 400, 404, delete 204 / 404. Stores `{{forecastId}}` |
| 11 Cleanup | Delete the users created by the run, then confirm 404 / 401 |

Every response is also checked at collection level: `X-Correlation-Id` is returned, and every 4xx/5xx is
`problem+json` with `errorCode`, `correlationId` (equal to the header), `service`, `timestamp`, a body
`status` equal to the HTTP status, and no stack trace or query text in `detail`.

## Notes

- Folder 03 needs the `BU_HIERARCHY` and `GEO_HIERARCHY` tables in KDB+; without them those requests fail
  with 503/502 (which is itself the correct error handling).
- `GET /ccar/forecast/forecast-ccar` was removed from ccar-hierarchy-service; folder 07 checks that it now returns 404.
- Folder 10 keeps data in memory only, so a restart of ccar-forecasting-service clears it.
- The expired / wrong-signature tokens are built in a pre-request script with `jwtSecret`. If you change
  `ccar.security.jwt.secret`, update the environment value too.
- The environment holds the local dev JWT secret that is already in `application.properties`. Don't reuse
  it outside local development.
