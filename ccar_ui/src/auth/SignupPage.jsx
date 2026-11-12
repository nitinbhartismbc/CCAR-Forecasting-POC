import {useState} from "react";
import {api, USE_MOCK} from "../api/client.js";
import {ApiError, messageFor} from "../api/errors.js";
import AuthLayout from "./AuthLayout.jsx";

// Mirrors the backend rule in UserRequest (PASSWORD_MIN_LENGTH = 8).
export const PASSWORD_MIN_LENGTH = 8;
const EMAIL_RE = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

// Same rules as the backend's UserRequest, plus "confirm password", checked before the call.
function validate(f) {
    const errors = {};
    if (!f.name.trim()) errors.name = "Enter your full name.";
    if (!f.email.trim()) errors.email = "Enter your email address.";
    else if (!EMAIL_RE.test(f.email.trim())) errors.email = "Enter a valid email address, like name@bank.com.";
    if (f.password.length < PASSWORD_MIN_LENGTH) {
        errors.password = `Use at least ${PASSWORD_MIN_LENGTH} characters.`;
    }
    if (!errors.password && f.confirm !== f.password) errors.confirm = "The passwords don’t match.";
    return errors;
}

const EMPTY = {name: "", email: "", password: "", confirm: "", phone: "", address: ""};

/**
 * Creates an account through POST /users. It does not sign the user in: on success it hands the email
 * back so the sign-in page can open pre-filled with a confirmation message.
 */
export default function SignupPage({onCreated, onSignIn}) {
    const [form, setForm] = useState(EMPTY);
    const [fieldErrors, setFieldErrors] = useState({});
    const [error, setError] = useState(null);
    const [busy, setBusy] = useState(false);

    const set = (key) => (e) => setForm((f) => ({...f, [key]: e.target.value}));

    const submit = async (e) => {
        e.preventDefault();
        const errors = validate(form);
        setFieldErrors(errors);
        setError(null);
        if (Object.keys(errors).length) return;

        setBusy(true);
        try {
            const email = form.email.trim();
            await api.register({
                name: form.name.trim(),
                email,
                password: form.password,
                phone: form.phone.trim(),
                address: form.address.trim(),
            });
            onCreated(email);
        } catch (err) {
            if (err instanceof ApiError && err.errorCode === "VALIDATION_FAILED" && err.errors?.length) {
                setFieldErrors(Object.fromEntries(err.errors.map((fe) => [fe.field, fe.message])));
            } else if (err instanceof ApiError && err.errorCode === "DUPLICATE_EMAIL") {
                setFieldErrors({email: messageFor(err)});
            } else {
                setError(err);
            }
        } finally {
            setBusy(false);
        }
    };

    const field = (key, label, props = {}, hint = null) => (
        <div className={`login-field ${fieldErrors[key] ? "has-error" : ""}`}>
            <label htmlFor={`signup-${key}`}>{label}</label>
            <input id={`signup-${key}`} value={form[key]} onChange={set(key)}
                   aria-invalid={!!fieldErrors[key]}
                   aria-describedby={fieldErrors[key] ? `signup-${key}-error` : hint ? `signup-${key}-hint` : undefined}
                   {...props}/>
            {fieldErrors[key]
                ? <span id={`signup-${key}-error`} className="field-error">{fieldErrors[key]}</span>
                : hint && <span id={`signup-${key}-hint`} className="field-hint">{hint}</span>}
        </div>
    );

    return (
        <AuthLayout>
            <form className="login-card" onSubmit={submit} noValidate aria-labelledby="signup-title">
                <div className="eyebrow">CCAR / FINANCIAL PLANNING</div>
                <h1 id="signup-title">Create an account</h1>
                <p className="login-sub">You’ll sign in with this email and password once the account is created.</p>

                {error && (
                    <div className="login-error" role="alert">
                        <span>{messageFor(error)}</span>
                        {error.correlationId && error.correlationId !== "mock" &&
                            <small>Reference: {error.correlationId}</small>}
                    </div>
                )}

                {field("name", "Full name", {autoComplete: "name", autoFocus: true})}
                {field("email", "Email", {type: "email", autoComplete: "email"})}
                {field("password", "Password", {type: "password", autoComplete: "new-password"},
                    `At least ${PASSWORD_MIN_LENGTH} characters.`)}
                {field("confirm", "Confirm password", {type: "password", autoComplete: "new-password"})}

                <div className="login-optional">
                    <span>Optional</span>
                </div>
                {field("phone", "Phone", {type: "tel", autoComplete: "tel"})}
                {field("address", "Address", {autoComplete: "street-address"})}

                <button type="submit" className="button primary login-submit" disabled={busy}>
                    {busy ? "Creating account…" : "Create account"}
                </button>

                <p className="login-switch">Already have an account?{" "}
                    <button type="button" className="link-btn" onClick={onSignIn}>Sign in</button>
                </p>

                {USE_MOCK && (
                    <p className="login-hint">Mock mode: accounts exist only until the page reloads.
                        “taken@example.com” is already registered.</p>
                )}
            </form>
        </AuthLayout>
    );
}
