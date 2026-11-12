# CCAR Forecasting UI

React + Vite UI for the CCAR forecast. All data comes from the backend through the API gateway on
`http://localhost:8089`, after signing in.

## How to Run this CCAR UI

```shell
npm install
npm run dev        # http://localhost:5173
```

`.env.development` has `VITE_USE_MOCK=true`: the UI uses `src/api/mock.js` and the sign-in page accepts any
email and password (the password `wrong` shows the error message). Set it to `false` to call the backend. Vite
proxies `/ccar/*` and `/users/*` to the gateway (`http://localhost:8089`, override with `VITE_GATEWAY_URL`), so
no CORS setup is needed in development.

## Sign-in and session

1. The app opens on the **Sign in** page when there is no valid session.
2. The page checks the fields like the backend does (email required and well-formed, password required), then
   calls `POST /users/login`. Wrong credentials show "The email or password is incorrect."; server-side field
   errors are shown under each field.
3. The response `{token, expiresIn, userId}` is stored in `sessionStorage` (`ccar.session`, with the email
   and the expiry time). It survives a page reload but not closing the tab.
4. Every API call sends `Authorization: Bearer <token>` (`src/api/client.js`).
5. The user goes back to the sign-in page, with a message, when:
   - the token's lifetime runs out (15 s early, so no call leaves with a dying token),
   - any call returns 401 (`TOKEN_EXPIRED`, `TOKEN_INVALID`, `UNAUTHORIZED`),
   - they press **Sign out** in the top bar.

## Sign-up

**Create an account** on the sign-in page opens the sign-up page:

- Fields: full name, email, password, confirm password; phone and address are optional.
- Checked before sending, with the same rules as the backend: name required, valid email, password at least
  8 characters, and both passwords match. Server-side field errors are shown under each field; an email that
  is already registered shows "An account with this email already exists. Sign in instead."
- It calls `POST /users` and does **not** sign the user in. On success it returns to the sign-in page with
  the email pre-filled and the message "Account created. Sign in with your new password."
- Mock mode: accounts exist until the page reloads; `taken@example.com` is treated as already registered.

Code: `src/auth/session.js` (storage), `src/auth/LoginPage.jsx` (sign-in form), `src/auth/SignupPage.jsx`
(sign-up form), `src/auth/AuthLayout.jsx` (shared frame), `src/auth/AuthGate.jsx` (switches between sign-in,
sign-up and the app).

## Screens
- **Forecast**: left nav has option to switch between Business unit and Geography hierarchies.
  Right side has two tab based views: **Aggregate** (Balance / Revenue / Expense rolled up for the selected node, chart + grid)
  and **Details** (paged, searchable list of the entities directly below the node. The option of "Drill in" goes one level further).
- **Actual Data**: T0, T-1, T-2 ... actual balance, revenue and expense data for the selected nodes.
- **Comparison**: forecast vs actual data by calendar month, variance = actual − forecast.
- Everything is displayed in USD; there is no currency selector on UI. Currency conversion to happen in the java backend.
- T0 is always a quarter-end date returned by Java API. T+1..T+36 are months post T0.

## Backend Contract (Proposed)

> **Notes**
>
> - All monetary values are in **USD**
> - Amounts are represented as **plain numbers**
> - Pagination is **0-based**

| Endpoint | Parameters | Response |
|-----------|------------|----------|
| `GET /ccar/bu/hierarchy` | `type=BU \| GEO` | Flat list:<br>`[{ id, name, level, parentId, childCount }]`<br><br>A nested tree structure with `children` is also supported. |
| `GET /ccar/portfolios` | None | `[{ id, name }]` |
| `GET /ccar/t0-dates` | None | `["2025-12-31", ...]`<br>Quarter-end dates for which data is available. |
| `GET /ccar/forecast` | One of:<br>- `buId`<br>- `geoId`<br>- `opCodeId`<br><br>Additional:<br>- `portfolioId` (`ALL` or `PORT_0001`)<br>- `t0Date` | ```json { "t0Date": "2025-12-31", "currency": "USD", "periods": ["T0", "T+1", "...", "T+36"], "rows": [ { "accountId": "...", "measure": "...", "values": [...] } ] } ``` |
| `GET /ccar/forecast/children` | Same filters as Forecast plus:<br>- `page`<br>- `size`<br>- `search` | ```json { "items": [ { "id": "...", "name": "...", "kind": "BU|GEO|OP_CODE", "level": 1, "childCount": 5, "rows": [...] } ], "page": 0, "size": 20, "totalElements": 100, "totalPages": 5 } ``` |
| `GET /ccar/actuals` | Same as Forecast | Same response structure as Forecast, but periods are returned as:<br>`["T0", "T-1", ...]` |
| `GET /ccar/comparison` | Same as Forecast | ```json { "t0Date": "2025-12-31", "currency": "USD", "rows": [ { "month": "2026-01", "period": "T+1", "accountId": "...", "measure": "...", "forecast": 1000, "actual": 950, "variance": -50, "variancePct": -5 } ] } ``` |

---

### Forecast Filters

The following hierarchical filters are mutually exclusive:

- `buId`
- `geoId`
- `opCodeId`

Additional filters:

- `portfolioId`
    - `ALL`
    - `PORT_0001`
    - Other supported portfolio identifiers
- `t0Date`
    - Quarter-end reference date

---

### Response Conventions

| Field | Description |
|---------|-------------|
| `currency` | Currency of returned values (currently `USD`) |
| `periods` | Relative reporting periods (`T0`, `T+1`, ..., `T+36`) |
| `rows` | Time-series data grouped by account and measure |
| `childCount` | Number of direct descendants under the node |
| `page` | Zero-based page index |
| `totalElements` | Total matching records |
| `totalPages` | Total available pages |

**Accounts**: 210001 Balance, 400001 Revenue, 500001 Expense.
If the Java JSON differs, adapt it in `src/api/client.js` (`toNodes` for the hierarchy) and nowhere else.

## Assumptions to check and confirm with the Java team
- Geo hierarchy uses the same endpoint with `type=GEO`.
- Op codes sit under level-6 unit BUs (`PARENT_BU_ID`) and directly under geo countries.
- Comparison matches forecast and actuals by calendar month; months with no actuals are omitted.
- Aggregation, FX conversion and variance are calculated in Java/KDB+, never in React.

## Structure

```text
src/
  App.jsx            shell, filters, selection state
  api/client.js      all HTTP calls + hierarchy adapter
  api/mock.js        local mock backend (for testing purpose only)
  components/        Sidebar (hierarchy nav), Common (chart, grids, pager)
  pages/             ForecastPage, ActualsPage, ComparisonPage
  useAsync.js
  utils.js
  styles.css
```

## PENDING TASKS: 

### 1. Measure Selection with Checkboxes
- Display all available measures.
- Provide checkbox options for users to select one or more of the following measures:
    - Balance
    - Revenue
    - Expenses

### 2. Clickable Breadcrumb Navigation
- Implement clickable breadcrumbs to enable navigation back to higher levels in the hierarchy.
- Display the current navigation path and allow users to return to any previous level with a single click.

### 3. Drill Navigation Synchronization
- Synchronize drill-down and drill-up actions with the left navigation panel.
- Automatically select and highlight the corresponding node in the left navigation based on the data currently displayed in the grid.

### 4. Actual Data Grid – Detailed Drill-In View
- Provide an Actual Data Grid view that displays detailed transactional data.
- Enable drill-in functionality to allow users to navigate seamlessly from summarized data to detailed record-level information.