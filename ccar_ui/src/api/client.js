// All backend calls live here.
// Endpoint contract: see README.md. Error contract: see ./errors.js.
//
// Every call:
//  - sends an X-Correlation-Id (one id per logical call, reused across retries) so the request can be traced
//    through gateway -> ccar-core / user-service logs and quoted by the user from the error message;
//  - sends the JWT (if signed in) as a Bearer token;
//  - times out after VITE_API_TIMEOUT_MS (default 30 s) and can be cancelled through an AbortSignal;
//  - retries idempotent GETs on network errors and 502/503 (honouring Retry-After, capped), never on 4xx/504;
//  - turns any failure into an ApiError parsed from the backend's problem+json body;
//  - on 401 clears the token and fires AUTH_EXPIRED_EVENT so the app can ask the user to sign in again.

import {ApiError, AUTH_EXPIRED_EVENT} from "./errors.js";

export const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";
const BASE = import.meta.env.VITE_API_BASE || "";
const TIMEOUT_MS = Number(import.meta.env.VITE_API_TIMEOUT_MS) || 30000;
const MAX_RETRIES = 2;
const MAX_RETRY_DELAY_MS = 4000;
const TOKEN_KEY = "ccar.accessToken";

// ---- access token (sessionStorage; may be unavailable in private mode) -----------------------------------
export function getAccessToken() {
  try {
    return sessionStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function setAccessToken(token) {
  try {
    sessionStorage.setItem(TOKEN_KEY, token);
  } catch {
    /* storage unavailable: token lives only for this call chain */
  }
}

export function clearAccessToken() {
  try {
    sessionStorage.removeItem(TOKEN_KEY);
  } catch {
    /* nothing to clear */
  }
}

// ---- helpers ----------------------------------------------------------------------------------------------
function newCorrelationId() {
  return globalThis.crypto?.randomUUID?.()
      ?? `ui-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

function sleep(ms, signal) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(resolve, ms);
    signal?.addEventListener("abort", () => {
      clearTimeout(timer);
      reject(signal.reason ?? new DOMException("Aborted", "AbortError"));
    }, {once: true});
  });
}

async function toApiError(res, path, correlationId) {
  let body = null;
  if ((res.headers.get("content-type") || "").includes("json")) {
    try {
      body = await res.json();
    } catch {
      /* error body was not JSON after all */
    }
  }
  return new ApiError({
    status: res.status,
    errorCode: body?.errorCode ?? null,
    title: body?.title ?? res.statusText,
    detail: body?.detail ?? `${res.status} ${res.statusText || ""} on ${path}`.trim(),
    correlationId: body?.correlationId ?? res.headers.get("X-Correlation-Id") ?? correlationId,
    retryAfter: Number(res.headers.get("Retry-After")) || null,
    errors: body?.errors ?? [],
    path,
  });
}

function isRetryable(error) {
  if (error.errorCode === "NETWORK_ERROR") return true;
  // 504 / client timeout: the server is slow, retrying would only multiply the wait
  return (error.status === 502 || error.status === 503) && error.errorCode !== "CIRCUIT_OPEN";
}

// ---- one HTTP attempt -------------------------------------------------------------------------------------
async function attempt(url, path, signal, correlationId) {
  const timeout = new AbortController();
  const timer = setTimeout(() => timeout.abort(), TIMEOUT_MS);
  const onCallerAbort = () => timeout.abort(signal.reason);
  signal?.addEventListener("abort", onCallerAbort, {once: true});

  const headers = {
    Accept: "application/json, application/problem+json",
    "X-Correlation-Id": correlationId,
  };
  const token = getAccessToken();
  if (token) headers.Authorization = `Bearer ${token}`;

  try {
    const res = await fetch(url, {headers, signal: timeout.signal});
    if (!res.ok) throw await toApiError(res, path, correlationId);
    try {
      return await res.json();
    } catch {
      throw new ApiError({status: res.status, errorCode: "INVALID_RESPONSE",
        detail: `Response from ${path} is not valid JSON`, correlationId, path});
    }
  } catch (e) {
    if (e instanceof ApiError) throw e;
    if (signal?.aborted) throw e; // cancelled by the caller (e.g. component unmounted): not an error to show
    if (timeout.signal.aborted) {
      throw new ApiError({errorCode: "CLIENT_TIMEOUT",
        detail: `No response from ${path} within ${TIMEOUT_MS / 1000} s`, correlationId, path});
    }
    throw new ApiError({errorCode: "NETWORK_ERROR", detail: e?.message || "Network error", correlationId, path});
  } finally {
    clearTimeout(timer);
    signal?.removeEventListener("abort", onCallerAbort);
  }
}

async function http(path, params = {}, {signal} = {}) {
  const qs = new URLSearchParams();

  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== "") {
      qs.append(k, v);
    }
  });

  const q = qs.toString();
  const url = `${BASE}${path}${q ? `?${q}` : ""}`;
  const correlationId = newCorrelationId();

  for (let n = 0; ; n++) {
    try {
      return await attempt(url, path, signal, correlationId);
    } catch (e) {
      if (!(e instanceof ApiError)) throw e;
      if (e.status === 401) {
        clearAccessToken();
        window.dispatchEvent(new CustomEvent(AUTH_EXPIRED_EVENT, {detail: e}));
        throw e;
      }
      if (!isRetryable(e) || n >= MAX_RETRIES) throw e;
      const delay = Math.min(e.retryAfter ? e.retryAfter * 1000 : 500 * 2 ** n, MAX_RETRY_DELAY_MS);
      await sleep(delay, signal);
    }
  }
}

const call = async (path, params, opts) =>
    USE_MOCK
        ? (await import("./mock.js")).mockCall(path, params)
        : http(path, params, opts);

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

// Every function takes an optional trailing {signal} so callers (useAsync) can cancel stale requests.
export const api = {
  hierarchy: async (type, opts) =>
      toNodes(await call("/ccar/bu/hierarchy", { type }, opts)),

  portfolios: (opts) =>
      call("/ccar/portfolios", {}, opts),

  t0Dates: (opts) =>
      call("/ccar/t0-dates", {}, opts),

  forecast: (p, opts) =>
      call("/ccar/forecast", p, opts),

  forecastChildren: (p, opts) =>
      call("/ccar/forecast/children", p, opts),

  actuals: (p, opts) =>
      call("/ccar/actuals", p, opts),

  comparison: (p, opts) =>
      call("/ccar/comparison", p, opts),
};