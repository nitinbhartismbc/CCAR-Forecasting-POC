// Local stand-in for the Java service (VITE_USE_MOCK=true).
// Responses follow the contract in README.md.
//
// Synthetic numbers only; aggregates scale with node size
// but are not exact sums of their children.

import { monthKey } from "../utils.js";
import { ApiError } from "./errors.js";

const delay = (ms) => new Promise((r) => setTimeout(r, ms));

const hash = (s) => {
  let h = 2166136261;
  for (const c of String(s)) {
    h ^= c.charCodeAt(0);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
};

const UNITS = 40;
const OPS = 7;
const GEO_OPS = 60;

const T0_DATES = [
  "2025-12-31",
  "2026-03-31",
  "2026-06-30",
  "2026-09-30",
];

const LATEST_ACTUAL_MONTH = "2026-09";

const PERIODS = Array.from(
    { length: 37 },
    (_, i) => (i ? `T+${i}` : "T0")
);

const PORTFOLIOS = [
  "Retail First-Lien Mortgages",
  "Home Equity Loans and Lines",
  "Retail Auto Loans",
  "Credit Card Revolving Balances",
  "Credit Card Transactor Portfolio",
  "Unsecured Personal Loans",
  "Secured Personal Loans",
  "Student Lending",
  "Retail Checking Accounts",
  "Retail Savings Accounts",
  "Retail Certificates of Deposit",
  "Retail Overdraft Facilities",
  "Small Business Term Loans",
  "Small Business Credit Lines",
  "Commercial and Industrial Loans",
].map((name, i) => ({
  id: `PORT_${String(i + 1).padStart(4, "0")}`,
  name,
}));

const REGIONS = {
  "North America": ["United States", "Canada"],
  Europe: ["United Kingdom", "Germany"],
  "Asia Pacific": ["Singapore", "Japan"],
  "Latin America": ["Mexico", "Brazil"],
  "Middle East & Africa": ["United Arab Emirates"],
};

const L4 = {
  "Consumer Banking": [
    "Deposits",
    "Cards",
    "Personal Lending",
    "Mortgages",
    "Wealth & Investments",
  ],
  "Commercial Banking": [
    "Small Business",
    "Middle Market",
    "Commercial Real Estate",
    "Trade Finance",
    "Cash Management",
  ],
};

const tree = () => {
  const nodes = [];
  const idx = {};
  let next = 1;

  const add = (name, level, parentId) => {
    const n = {
      id: next++,
      name,
      level,
      parentId,
      childCount: 0,
    };

    nodes.push(n);
    idx[n.id] = n;

    if (parentId != null) {
      idx[parentId].childCount++;
    }

    return n;
  };

  return { nodes, idx, add };
};

const B = tree();
const G = tree();

const bRoot = B.add("ALL_BANK", 1, null);
const gRoot = G.add("ALL_GEO", 1, null);

for (const [region, countries] of Object.entries(REGIONS)) {
  const br = B.add(region, 2, bRoot.id);
  const gr = G.add(region, 2, gRoot.id);

  for (const country of countries) {
    const bc = B.add(country, 3, br.id);
    G.add(country, 3, gr.id);

    for (const [l4, l5s] of Object.entries(L4)) {
      const n4 = B.add(l4, 4, bc.id);

      l5s.forEach((x) => B.add(x, 5, n4.id));
    }
  }
}

B.nodes
    .filter((n) => n.level === 5)
    .forEach((n) => (n.childCount = UNITS));

G.nodes
    .filter((n) => n.level === 3)
    .forEach((n) => (n.childCount = GEO_OPS));

// ids:
// nav nodes < 100000
// level-6 units 100000..9999999
// op codes >= 10000000

const l5Count = (id) => {
  const children = B.nodes.filter((n) => n.parentId === id);

  return children.length
      ? children.reduce((sum, x) => sum + l5Count(x.id), 0)
      : 1;
};

const geoWeight = (id) => {
  const children = G.nodes.filter((n) => n.parentId === id);

  return children.length
      ? children.reduce((sum, x) => sum + geoWeight(x.id), 0)
      : 10;
};

const opEntity = (id) => ({
  id,
  name: `OPC-${id}`,
  kind: "OP_CODE",
  level: 7,
  childCount: 0,
  weight: 1 / (UNITS * OPS),
});

const unitEntity = (id) => {
  const u = id - 100000;
  const l5 = B.idx[Math.floor(u / 100)];

  return {
    id,
    name: `${l5.name} - Unit ${String(u % 100).padStart(6, "0")}`,
    kind: "BU",
    level: 6,
    childCount: OPS,
    weight: 1 / UNITS,
  };
};

const buEntity = (n) => ({
  ...n,
  kind: "BU",
  weight: l5Count(n.id),
});

const geoEntity = (n) => ({
  ...n,
  kind: "GEO",
  weight: geoWeight(n.id),
});

function entity(p) {
  if (p.opCodeId) {
    return opEntity(+p.opCodeId);
  }

  if (p.geoId) {
    return geoEntity(G.idx[p.geoId]);
  }

  const id = +p.buId;

  return id >= 100000
      ? unitEntity(id)
      : buEntity(B.idx[id]);
}

function childEntities(p) {
  const e = entity(p);

  if (e.kind === "GEO") {
    return e.level < 3
        ? G.nodes
            .filter((n) => n.parentId === e.id)
            .map(geoEntity)
        : Array.from(
            { length: GEO_OPS },
            (_, j) => opEntity(20000000 + e.id * 100 + j + 1)
        );
  }

  if (e.kind === "BU" && e.level === 6) {
    return Array.from(
        { length: OPS },
        (_, j) => opEntity(10000000 + e.id * 10 + j + 1)
    );
  }

  if (e.kind === "BU" && e.level === 5) {
    return Array.from(
        { length: UNITS },
        (_, i) => unitEntity(100000 + e.id * 100 + i + 1)
    );
  }

  return e.kind === "BU"
      ? B.nodes
          .filter((n) => n.parentId === e.id)
          .map(buEntity)
      : [];
}

const ACCOUNTS = {
  "210001": ["Balance", 100000, 0.006, 0.025],
  "400001": ["Revenue", 1000, 0.008, -0.035],
  "500001": ["Expense", 10000, 0.0055, 0.045],
};

const pf = (id) =>
    !id || id === "ALL"
        ? 1
        : 0.2 + (Number(id.slice(-2)) % 5) * 0.06;

const startVal = (acct, e, portfolio, t0) =>
    ACCOUNTS[acct][1] *
    e.weight *
    (0.8 + (hash(`${e.kind}${e.id}${acct}`) % 40) / 100) *
    pf(portfolio) *
    (1 + 0.02 * Math.max(0, T0_DATES.indexOf(t0)));

const fc = (acct, e, portfolio, t0) => {
  const s = startVal(acct, e, portfolio, t0);
  const g = ACCOUNTS[acct][2];
  const ph = hash(acct + e.id) % 7;

  return PERIODS.map((_, i) =>
      Math.round(
          s * (1 + g * i) +
          Math.sin(i / 2.7 + ph) * s * 0.012
      )
  );
};

const actualAt = (acct, forecast, e, n) =>
    Math.round(
        forecast *
        (1 +
            ACCOUNTS[acct][3] *
            Math.sin(
                n * 1.25 + (hash(acct + e.id) % 7)
            ))
    );

const fcRows = (e, portfolio, t0) =>
    Object.keys(ACCOUNTS).map((a) => ({
      accountId: a,
      measure: ACCOUNTS[a][0],
      values: fc(a, e, portfolio, t0),
    }));

// Mock sign-in: any well-formed email with a non-empty password works, except the password "wrong",
// which returns the same 401 the real service gives for bad credentials.
export async function mockLogin(email, password) {
  await delay(300);
  const errors = [];
  if (!email) errors.push({field: "email", message: "Email is required"});
  else if (!/^[^@\s]+@[^@\s]+$/.test(email)) errors.push({field: "email", message: "Email must be valid"});
  if (!password) errors.push({field: "password", message: "Password is required"});
  if (errors.length) {
    throw new ApiError({status: 400, errorCode: "VALIDATION_FAILED", detail: "Request validation failed",
      errors, correlationId: "mock", path: "/users/login"});
  }
  if (password === "wrong") {
    throw new ApiError({status: 401, errorCode: "INVALID_CREDENTIALS", detail: "Invalid email or password",
      correlationId: "mock", path: "/users/login"});
  }
  return {token: `mock.${Date.now().toString(36)}.token`, expiresIn: 3600, userId: 1};
}

// Mock sign-up: same rules as the backend's UserRequest. Emails registered in this page session (and
// "taken@example.com") return 409 DUPLICATE_EMAIL.
const mockEmails = new Set(["taken@example.com"]);
export async function mockRegister({name, email, password, phone, address}) {
  await delay(300);
  const errors = [];
  if (!name?.trim()) errors.push({field: "name", message: "name is required"});
  if (!email?.trim()) errors.push({field: "email", message: "email is required"});
  else if (!/^[^@\s]+@[^@\s]+$/.test(email)) errors.push({field: "email", message: "email must be valid"});
  if (password == null || password.length < 8) {
    errors.push({field: "password", message: "password must be at least 8 characters"});
  }
  if (errors.length) {
    throw new ApiError({status: 400, errorCode: "VALIDATION_FAILED", detail: "Request validation failed",
      errors, correlationId: "mock", path: "/users"});
  }
  if (mockEmails.has(email.toLowerCase())) {
    throw new ApiError({status: 409, errorCode: "DUPLICATE_EMAIL",
      detail: `A user with email ${email} already exists`, correlationId: "mock", path: "/users"});
  }
  mockEmails.add(email.toLowerCase());
  return {id: mockEmails.size, name, email, phone: phone ?? null, address: address ?? null,
    createdAt: new Date().toISOString()};
}

export async function mockCall(path, p = {}) {
  await delay(150 + Math.random() * 250);

  const t0 =
      p.t0Date || T0_DATES[T0_DATES.length - 1];

  const portfolio = p.portfolioId || "ALL";

  if (path === "/ccar/bu/hierarchy") {
    return (p.type === "GEO" ? G : B).nodes.map(
        (n) => ({ ...n })
    );
  }

  if (path === "/ccar/portfolios") {
    return PORTFOLIOS;
  }

  if (path === "/ccar/t0-dates") {
    return T0_DATES;
  }

  if (path === "/ccar/forecast") {
    return {
      t0Date: t0,
      currency: "USD",
      periods: PERIODS,
      rows: fcRows(entity(p), portfolio, t0),
    };
  }

  if (path === "/ccar/forecast/children") {
    const q = (p.search || "").toLowerCase();
    const page = +p.page || 0;
    const size = +p.size || 25;

    const all = childEntities(p).filter(
        (c) =>
            !q ||
            c.name.toLowerCase().includes(q) ||
            String(c.id).includes(q)
    );

    return {
      t0Date: t0,
      currency: "USD",
      periods: PERIODS,
      page,
      size,
      totalElements: all.length,
      totalPages: Math.max(
          1,
          Math.ceil(all.length / size)
      ),

      items: all
          .slice(page * size, page * size + size)
          .map((c) => ({
            id: c.id,
            name: c.name,
            kind: c.kind,
            level: c.level,
            childCount: c.childCount,
            rows: fcRows(c, portfolio, t0),
          })),
    };
  }

  if (path === "/ccar/actuals") {
    const e = entity(p);

    const periods = Array.from(
        { length: 13 },
        (_, k) => (k ? `T-${k}` : "T0")
    );

    const rows = Object.keys(ACCOUNTS).map((a) => {
      const s = startVal(a, e, portfolio, t0);
      const ph = hash(a + e.id) % 7;

      return {
        accountId: a,
        measure: ACCOUNTS[a][0],
        values: periods.map((_, k) =>
            Math.round(
                s *
                (1 - ACCOUNTS[a][2] * k) *
                (1 +
                    ACCOUNTS[a][3] *
                    Math.sin(k * 1.1 + ph))
            )
        ),
      };
    });

    return {
      t0Date: t0,
      currency: "USD",
      periods,
      rows,
    };
  }

  if (path === "/ccar/comparison") {
    const e = entity(p);
    const rows = [];

    for (let n = 1; n <= 36; n++) {
      const month = monthKey(t0, n);

      if (month > LATEST_ACTUAL_MONTH) {
        break;
      }

      for (const a of Object.keys(ACCOUNTS)) {
        const forecast = fc(
            a,
            e,
            portfolio,
            t0
        )[n];

        const actual = actualAt(
            a,
            forecast,
            e,
            n
        );

        const variance = actual - forecast;

        rows.push({
          month,
          period: `T+${n}`,
          accountId: a,
          measure: ACCOUNTS[a][0],
          forecast,
          actual,
          variance,
          variancePct: forecast
              ? (variance / forecast) * 100
              : 0,
        });
      }
    }

    return {
      t0Date: t0,
      currency: "USD",
      rows,
    };
  }

  throw new Error(`No mock for ${path}`);
}