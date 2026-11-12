import {useState} from "react";
import {api, USE_MOCK} from "../api/client.js";
import {ApiError, messageFor} from "../api/errors.js";
import AuthLayout from "./AuthLayout.jsx";

const EMAIL_RE = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

// Same rules as the backend's LoginRequest (@NotBlank, @Email), checked before the call so the user gets
// instant feedback; the server's VALIDATION_FAILED field errors are shown the same way. No length rule here:
// accounts created before the 8-character minimum must still be able to sign in.
function validate(email, password) {
    const errors = {};
    if (!email.trim()) errors.email = "Enter your email address.";
    else if (!EMAIL_RE.test(email.trim())) errors.email = "Enter a valid email address, like name@bank.com.";
    if (!password) errors.password = "Enter your password.";
    return errors;
}

/**
 * @param notice        {tone: "success" | "warn", text} shown above the form (account created, session expired...)
 * @param initialEmail  pre-fills the email, e.g. right after sign-up
 */
export default function LoginPage({notice, initialEmail = "", onSignedIn, onCreateAccount}) {
    const [email, setEmail] = useState(initialEmail);
    const [password, setPassword] = useState("");
    const [fieldErrors, setFieldErrors] = useState({});
    const [error, setError] = useState(null);
    const [busy, setBusy] = useState(false);

    const submit = async (e) => {
        e.preventDefault();
        const errors = validate(email, password);
        setFieldErrors(errors);
        setError(null);
        if (Object.keys(errors).length) return;

        setBusy(true);
        try {
            const session = await api.login(email.trim(), password);
            onSignedIn(session);
        } catch (err) {
            if (err instanceof ApiError && err.errorCode === "VALIDATION_FAILED" && err.errors?.length) {
                setFieldErrors(Object.fromEntries(err.errors.map((fe) => [fe.field, fe.message])));
            } else {
                setError(err);
            }
            setPassword("");
        } finally {
            setBusy(false);
        }
    };

    return (
        <AuthLayout>
            <form className="login-card" onSubmit={submit} noValidate aria-labelledby="login-title">
                <div className="eyebrow">CCAR / FINANCIAL PLANNING</div>
                <h1 id="login-title">Sign in</h1>
                <p className="login-sub">Use your CCAR account to view forecasts, actuals and comparisons.</p>

                {notice && (
                    <div className={`info-banner ${notice.tone === "warn" ? "warn" : "success"}`} role="status">
                        <span className="info-icon">{notice.tone === "warn" ? "!" : "✓"}</span>{notice.text}
                    </div>
                )}

                {error && (
                    <div className="login-error" role="alert">
                        <span>{messageFor(error)}</span>
                        {error.correlationId && error.correlationId !== "mock" &&
                            <small>Reference: {error.correlationId}</small>}
                    </div>
                )}

                <div className={`login-field ${fieldErrors.email ? "has-error" : ""}`}>
                    <label htmlFor="login-email">Email</label>
                    <input id="login-email" type="email" autoComplete="username" autoFocus={!initialEmail}
                           value={email} onChange={(e) => setEmail(e.target.value)}
                           aria-invalid={!!fieldErrors.email}
                           aria-describedby={fieldErrors.email ? "login-email-error" : undefined}/>
                    {fieldErrors.email && <span id="login-email-error" className="field-error">{fieldErrors.email}</span>}
                </div>

                <div className={`login-field ${fieldErrors.password ? "has-error" : ""}`}>
                    <label htmlFor="login-password">Password</label>
                    <input id="login-password" type="password" autoComplete="current-password"
                           autoFocus={!!initialEmail}
                           value={password} onChange={(e) => setPassword(e.target.value)}
                           aria-invalid={!!fieldErrors.password}
                           aria-describedby={fieldErrors.password ? "login-password-error" : undefined}/>
                    {fieldErrors.password &&
                        <span id="login-password-error" className="field-error">{fieldErrors.password}</span>}
                </div>

                <button type="submit" className="button primary login-submit" disabled={busy}>
                    {busy ? "Signing in…" : "Sign in"}
                </button>

                <p className="login-switch">New to CCAR Forecasting?{" "}
                    <button type="button" className="link-btn" onClick={onCreateAccount}>Create an account</button>
                </p>

                {USE_MOCK && (
                    <p className="login-hint">Mock mode: any email and password signs in. Use the password
                        “wrong” to see the error.</p>
                )}
            </form>
        </AuthLayout>
    );
}
