// The signed-in session: the JWT from POST /users/login plus when it expires and who it belongs to.
// Kept in sessionStorage, so it survives a page reload but not closing the tab. Storage can be unavailable
// (private mode, blocked site data); then the session lives only until the page reloads.

const SESSION_KEY = "ccar.session";
// Treat the token as expired slightly early, so a request never leaves with a token that dies in flight.
const EXPIRY_SKEW_MS = 15_000;

let memorySession = null;

function read() {
    try {
        const raw = sessionStorage.getItem(SESSION_KEY);
        return raw ? JSON.parse(raw) : memorySession;
    } catch {
        return memorySession;
    }
}

/** The current session, or null when there is none or it has expired. */
export function getSession() {
    const s = read();
    if (!s?.token || !s.expiresAt) return null;
    if (Date.now() >= s.expiresAt - EXPIRY_SKEW_MS) {
        clearSession();
        return null;
    }
    return s;
}

/** Stores the login response. Returns the stored session. */
export function saveSession({token, expiresIn, userId}, email) {
    const session = {token, userId, email, expiresAt: Date.now() + expiresIn * 1000};
    memorySession = session;
    try {
        sessionStorage.setItem(SESSION_KEY, JSON.stringify(session));
    } catch {
        /* storage unavailable: memorySession still holds it */
    }
    return session;
}

export function clearSession() {
    memorySession = null;
    try {
        sessionStorage.removeItem(SESSION_KEY);
    } catch {
        /* nothing to clear */
    }
}

/** Bearer token for API calls, or null when signed out / expired. */
export function getAccessToken() {
    return getSession()?.token ?? null;
}

/** Milliseconds until the session should be treated as expired (0 when already expired). */
export function msUntilExpiry(session) {
    return Math.max(0, session.expiresAt - EXPIRY_SKEW_MS - Date.now());
}
