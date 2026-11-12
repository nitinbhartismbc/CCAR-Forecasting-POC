import {useCallback, useEffect, useState} from "react";
import App from "../App.jsx";
import LoginPage from "./LoginPage.jsx";
import SignupPage from "./SignupPage.jsx";
import {api} from "../api/client.js";
import {AUTH_EXPIRED_EVENT, messageFor} from "../api/errors.js";
import {getSession, msUntilExpiry} from "./session.js";

// setTimeout can't wait longer than ~24.8 days; tokens live an hour, but stay safe.
const MAX_TIMER_MS = 2 ** 31 - 1;

/**
 * Shows sign-in (or sign-up) until there is a valid session, then the app.
 * Sign-up never signs in: it returns to sign-in with the new email pre-filled.
 * Returns to sign-in when:
 *  - the token's lifetime (expiresIn from the login response) runs out,
 *  - any API call gets a 401 (token expired / invalid on the server side),
 *  - the user signs out.
 */
export default function AuthGate() {
    const [session, setSession] = useState(() => getSession());
    const [view, setView] = useState("signin");           // "signin" | "signup" while signed out
    const [notice, setNotice] = useState(null);           // {tone, text} for the sign-in page
    const [prefillEmail, setPrefillEmail] = useState("");

    const endSession = useCallback((text) => {
        api.logout();
        setSession(null);
        setView("signin");
        setNotice({tone: "warn", text});
    }, []);

    // 401 from any authenticated call (fired by api/client.js)
    useEffect(() => {
        const onExpired = (e) => endSession(messageFor(e.detail));
        window.addEventListener(AUTH_EXPIRED_EVENT, onExpired);
        return () => window.removeEventListener(AUTH_EXPIRED_EVENT, onExpired);
    }, [endSession]);

    // sign out shortly before the token expires, instead of waiting for the first failing call
    useEffect(() => {
        if (!session) return undefined;
        const timer = setTimeout(
            () => endSession("Your session has expired. Please sign in again."),
            Math.min(msUntilExpiry(session), MAX_TIMER_MS));
        return () => clearTimeout(timer);
    }, [session, endSession]);

    if (session) {
        return <App session={session} onSignOut={() => endSession("You have signed out.")}/>;
    }

    if (view === "signup") {
        return <SignupPage
            onCreated={(email) => {
                setPrefillEmail(email);
                setNotice({tone: "success", text: "Account created. Sign in with your new password."});
                setView("signin");
            }}
            onSignIn={() => {
                setNotice(null);
                setView("signin");
            }}/>;
    }

    return <LoginPage
        key={prefillEmail}                 // remount so a new pre-filled email takes effect
        notice={notice}
        initialEmail={prefillEmail}
        onSignedIn={(s) => {
            setNotice(null);
            setPrefillEmail("");
            setSession(s);
        }}
        onCreateAccount={() => {
            setNotice(null);
            setView("signup");
        }}/>;
}
