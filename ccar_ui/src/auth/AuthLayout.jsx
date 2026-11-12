import {USE_MOCK} from "../api/client.js";

// Shared frame for the sign-in and sign-up pages: the app's top bar and a centred card area.
export default function AuthLayout({children}) {
    return (
        <div className="login-shell">
            <header className="topbar">
                <div className="brand-mark">CC</div>
                <div className="brand-copy">
                    <div className="brand-title">CCAR Forecasting</div>
                    <div className="brand-subtitle">Bank Risk &amp; Finance</div>
                </div>
                <div className="topbar-spacer"/>
                <span className="environment-tag">{USE_MOCK ? "POC · MOCK DATA" : "POC · LIVE BACKEND"}</span>
            </header>
            <main className="login-main">{children}</main>
        </div>
    );
}
