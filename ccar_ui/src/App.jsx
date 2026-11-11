import {useEffect, useMemo, useState} from "react";
import {api, USE_MOCK} from "./api/client.js";
import {useAsync} from "./useAsync.js";
import Sidebar from "./components/Sidebar.jsx";
import ForecastPage from "./pages/ForecastPage.jsx";
import ActualsPage from "./pages/ActualsPage.jsx";
import ComparisonPage from "./pages/ComparisonPage.jsx";
import {fmtDate, isQuarterEnd} from "./utils.js";
import {AUTH_EXPIRED_EVENT, messageFor} from "./api/errors.js";
import {ErrorNotice} from "./components/Common.jsx";

const TITLES = {
    forecast: ["Forecast View", "Base period and 36-month forecast"],
    actuals: ["Actual Data", "Reported results for the months up to T0"],
    comparison: ["Comparison View", "Compare actual results against the forecast"],
};

export default function App() {
    const [page, setPage] = useState("forecast");
    const [hier, setHier] = useState("BU");            // "BU" | "GEO"
    const [scope, setScope] = useState(null);          // { id, name, level, kind, childCount, trail, navId }
    const [portfolio, setPortfolio] = useState("ALL");
    const [t0, setT0] = useState("");
    const [authError, setAuthError] = useState(null); // set by the api client on any 401

    useEffect(() => {
        const onAuthExpired = (e) => setAuthError(e.detail);
        window.addEventListener(AUTH_EXPIRED_EVENT, onAuthExpired);
        return () => window.removeEventListener(AUTH_EXPIRED_EVENT, onAuthExpired);
    }, []);

    const nav = useAsync((signal) => api.hierarchy(hier, {signal}), [hier]);
    const pfs = useAsync((signal) => api.portfolios({signal}), []);
    const t0s = useAsync((signal) => api.t0Dates({signal}), []);
    const t0List = useMemo(() => [...(t0s.data || [])].sort(), [t0s.data]);
    const latestT0 = t0List[t0List.length - 1] || "";
    useEffect(() => {
        if (!t0 && latestT0) setT0(latestT0);
    }, [t0, latestT0]);

    const switchHier = (h) => {
        setHier(h);
        setScope(null);
    };
    const drill = (it) => setScope({
        id: it.id,
        name: it.name,
        level: it.level,
        kind: it.kind,
        childCount: it.childCount,
        trail: [...scope.trail, scope.name],
        navId: scope.navId
    });
    const [title, subtitle] = TITLES[page];
    const crumbs = scope ? [...scope.trail, scope.name] : [];
    const ready = scope && t0;
    const pageProps = {scope, t0, portfolio};

    return (
        <div className="app-shell">
            <header className="topbar">
                <div className="brand-mark">CC</div>
                <div className="brand-copy">
                    <div className="brand-title">CCAR Forecasting</div>
                    <div className="brand-subtitle">Bank Risk &amp; Finance</div>
                </div>
                <div className="topbar-spacer"/>
                <span className="environment-tag">{USE_MOCK ? "POC · MOCK DATA" : "POC · LIVE BACKEND"}</span>
                <div className="avatar">BP</div>
            </header>
            <div className="app-body">
                <Sidebar page={page} setPage={setPage} hier={hier} switchHier={switchHier} nav={nav} scope={scope}
                         onSelect={setScope} mock={USE_MOCK}/>
                <main className="main-content">
                    <div className="page-header">
                        <div>
                            <div className="eyebrow">CCAR / FINANCIAL PLANNING</div>
                            <h1>{title}</h1><p>{subtitle}{t0 && page !== "actuals" ? ` · T0 ${fmtDate(t0)}` : ""}</p>
                        </div>
                        <div className="header-actions"><span className="period-badge">All values in USD</span></div>
                    </div>
                    {crumbs.length > 0 &&
                        <div className="breadcrumb">{crumbs.map((c, i) => <span key={i} className="crumb">{i > 0 &&
                            <span className="crumb-separator">/</span>}<span
                            className={i === crumbs.length - 1 ? "current" : ""}>{c}</span></span>)}</div>}
                    <section className="filters-card">
                        <div className="filter-field"><label>{hier === "GEO" ? "Geography" : "Business unit"}</label>
                            <div className="selected-value"><span
                                className="bu-symbol">⌂</span>{scope ? scope.name : "Loading…"}<span
                                className="readonly-hint">From hierarchy</span></div>
                        </div>
                        <div className="filter-field"><label>T0 date (quarter-end)</label>
                            <select value={t0} onChange={(e) => setT0(e.target.value)} disabled={!t0List.length}>
                                {t0List.map((d) => <option key={d} value={d}>{fmtDate(d)}</option>)}
                            </select></div>
                        <div className="filter-field"><label>Portfolio</label>
                            <select value={portfolio} onChange={(e) => setPortfolio(e.target.value)}>
                                <option value="ALL">All portfolios</option>
                                {(pfs.data || []).map((p) => <option key={p.id}
                                                                     value={p.id}>{p.id} · {p.name}</option>)}
                            </select></div>
                        <button className="button secondary reset-button" onClick={() => {
                            setPortfolio("ALL");
                            setT0(latestT0);
                        }}>Reset filters
                        </button>
                    </section>
                    {t0 && !isQuarterEnd(t0) &&
                        <div className="info-banner warn"><span className="info-icon">!</span>T0 {t0} is not a
                            quarter-end date. Forecasts are only produced for 31 Mar, 30 Jun, 30 Sep and 31 Dec.</div>}
                    {authError &&
                        <div className="info-banner warn" role="alert"><span className="info-icon">!</span>
                            {messageFor(authError)}</div>}
                    {t0s.error && <ErrorNotice error={t0s.error} what="the T0 dates" onRetry={t0s.reload}/>}
                    {scope && <div className="scope-note"><span
                        className="info-icon">i</span><span>Selected scope: <strong>{scope.name}</strong> · Level {scope.level} · ID {scope.id}. A parent selection is the aggregate of everything below it, calculated by the backend.</span>
                    </div>}
                    {!ready && !nav.error && !t0s.error &&
                        <div className="state-box">Loading hierarchy and dates…</div>}
                    {ready && page === "forecast" && <ForecastPage {...pageProps} onDrill={drill}/>}
                    {ready && page === "actuals" && <ActualsPage {...pageProps} />}
                    {ready && page === "comparison" && <ComparisonPage {...pageProps} />}
                    <footer className="page-footer">
                        <span>CCAR Forecasting POC</span><span>{USE_MOCK ? "Sample data only · " : ""}Not for financial decision-making</span>
                    </footer>
                </main>
            </div>
        </div>
    );
}
