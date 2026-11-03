// All backend calls live here.
// Endpoint contract: see README.md.

export const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";
const BASE = import.meta.env.VITE_API_BASE || "";

async function http(path, params = {}) {
  const qs = new URLSearchParams();

  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== "") {
      qs.append(k, v);
    }
  });

  const q = qs.toString();

  const res = await fetch(
      `${BASE}${path}${q ? `?${q}` : ""}`,
      {
        headers: {
          Accept: "application/json",
        },
      }
  );

  if (!res.ok) {
    throw new Error(
        `${res.status} ${res.statusText || ""} on ${path}`.trim()
    );
  }

  return res.json();
}

const call = async (path, params) =>
    USE_MOCK
        ? (await import("./mock.js")).mockCall(path, params)
        : http(path, params);

/**
 * Turns whatever the hierarchy endpoint returns
 * (flat list or nested tree) into:
 *
 * [
 *   {
 *     id,
 *     name,
 *     level,
 *     parentId,
 *     childCount
 *   }
 * ]
 *
 * If the Java JSON uses other field names,
 * change only this function.
 */
function toNodes(raw, parent = null, out = []) {
  const list = Array.isArray(raw)
      ? raw
      : raw?.nodes || raw?.data || [raw];

  for (const n of list) {
    const kids = n.children || [];
    const id = n.id ?? n.buId ?? n.geoId;

    out.push({
      id,
      name: n.name ?? n.buName ?? n.geoName,
      level: n.level ?? n.levelId,
      parentId: n.parentId ?? parent,
      childCount: n.childCount ?? kids.length,
    });

    if (kids.length) {
      toNodes(kids, id, out);
    }
  }

  return out;
}

export const api = {
  hierarchy: async (type) =>
      toNodes(await call("/ccar/bu/hierarchy", { type })),

  portfolios: () =>
      call("/ccar/portfolios"),

  t0Dates: () =>
      call("/ccar/t0-dates"),

  forecast: (p) =>
      call("/ccar/forecast", p),

  forecastChildren: (p) =>
      call("/ccar/forecast/children", p),

  actuals: (p) =>
      call("/ccar/actuals", p),

  comparison: (p) =>
      call("/ccar/comparison", p),
};