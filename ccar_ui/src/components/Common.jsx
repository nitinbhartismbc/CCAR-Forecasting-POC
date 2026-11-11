import {useMemo} from "react";
import {measures, measureName, money, pickIdx, periodMonth} from "../utils.js";
import {messageFor} from "../api/errors.js";

// Shows a backend/network error the same way everywhere: what failed, a message chosen by errorCode, and the
// correlation id ("Reference") support can search for in every service log.
export function ErrorNotice({error, what, onRetry, className = "state-box error"}) {
    return (
        <div className={className} role="alert">
            <span>Couldn’t load {what}. {messageFor(error)}
                {error?.correlationId && <small>Reference: {error.correlationId}</small>}</span>
            {onRetry && <button className="button small" onClick={onRetry}>Retry</button>}
        </div>
    );
}

export function Async({state, what, children}) {
    if (state.loading) return <div className="state-box">Loading {what}…</div>;
    if (state.error) return <ErrorNotice error={state.error} what={what} onRetry={state.reload}/>;
    return children(state.data);
}

export function Pager({page, size, total, totalPages, onPage, onSize}) {
    const from = total ? page * size + 1 : 0, to = Math.min(total, (page + 1) * size);
    return (
        <div className="pager">
            <span>{from}–{to} of {total}</span>
            <select value={size} onChange={(e) => onSize(Number(e.target.value))} aria-label="Rows per page">
                {[10, 25, 50, 100].map((n) => <option key={n} value={n}>{n} per page</option>)}
            </select>
            <button className="button small" disabled={page <= 0} onClick={() => onPage(page - 1)}>Previous</button>
            <span>Page {page + 1} of {totalPages}</span>
            <button className="button small" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>Next
            </button>
        </div>
    );
}

export function PeriodHead({periods, idx, t0}) {
    return idx.map((i) => <th key={periods[i]}>{periods[i]}<span className="th-sub">{periodMonth(t0, periods[i])}</span>
    </th>);
}

export function MeasureCards({rows, caption, selected, onPick, signed}) {
    const byId = Object.fromEntries(rows.map((r) => [r.accountId, r]));
    return (
        <div className="measure-cards">
            {measures.map((m) => {
                const v = byId[m.id]?.value ?? byId[m.id]?.values?.[0];
                const inner = <>
                    <span className="measure-top"><span className="measure-dot"
                                                        style={{background: m.color}}/>{m.name}<span
                        className="measure-account">{m.id}</span></span>
                    <strong className={signed && v < 0 ? "negative" : ""}>{signed && v > 0 ? "+" : ""}{money(v)}
                        <small>USD</small></strong>
                    <span className="measure-caption">{caption}</span>
                </>;
                return onPick
                    ? <button key={m.id} className={`measure-card ${selected === m.id ? "measure-selected" : ""}`}
                              onClick={() => onPick(selected === m.id ? "" : m.id)}>{inner}</button>
                    : <div key={m.id} className="measure-card static-card">{inner}</div>;
            })}
        </div>
    );
}

export function TrendChart({rows, periods, selected, onSelect}) {
    const width = 800, height = 200, left = 42, right = 16, top = 12, bottom = 30, L = periods.length;
    const series = useMemo(() => rows.map((r) => {
        const mx = Math.max(...r.values) * 1.1, mn = Math.min(...r.values) * 0.9;
        const m = measures.find((x) => x.id === r.accountId);
        return {
            id: r.accountId, name: measureName(r.accountId, r.measure), color: m?.color || "#6b7f91",
            coords: r.values.map((v, i) => ({
                x: left + (i * (width - left - right)) / (L - 1),
                y: top + (height - top - bottom) * (1 - (v - mn) / (mx - mn || 1))
            }))
        };
    }), [rows, L]);
    const ticks = [0, 0.25, 0.5, 0.75, 1].map((f) => Math.round(f * (L - 1)));
    return (
        <div className="chart-card">
            <div className="section-heading">
                <div><h3>Forecast trend</h3><p>Each line is scaled to its own range, so compare shape and not size</p>
                </div>
                <div className="legend">{series.map((s) => <button key={s.id}
                                                                   className={`legend-item ${selected === s.id ? "active" : ""}`}
                                                                   onClick={() => onSelect(s.id)}><i
                    style={{background: s.color}}/>{s.name}</button>)}</div>
            </div>
            <svg className="chart" viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Forecast trend chart">
                {[0, 1, 2, 3].map((i) => <g key={i}>
                    <line x1={left} y1={top + (i * (height - top - bottom)) / 3} x2={width - right}
                          y2={top + (i * (height - top - bottom)) / 3} stroke="#e5eaf0" strokeDasharray="4 4"/>
                    <text x={left - 9} y={top + (i * (height - top - bottom)) / 3 + 4} textAnchor="end" fontSize="10"
                          fill="#738196">{i === 0 ? "High" : i === 3 ? "Low" : ""}</text>
                </g>)}
                {ticks.map((i) => <text key={i} x={left + (i * (width - left - right)) / (L - 1)} y={height - 8}
                                        textAnchor="middle" fontSize="10" fill="#738196">{periods[i]}</text>)}
                {series.map((s) => (
                    <g key={s.id} opacity={!selected || selected === s.id ? 1 : 0.35}>
                        <polyline points={s.coords.map((p) => `${p.x},${p.y}`).join(" ")} fill="none" stroke={s.color}
                                  strokeWidth={selected === s.id ? 3 : 2} strokeLinecap="round" strokeLinejoin="round"/>
                        {s.coords.filter((_, i) => i % 3 === 0).map((p, i) => <circle key={i} cx={p.x} cy={p.y}
                                                                                      r={selected === s.id ? 3 : 2}
                                                                                      fill={s.color}/>)}
                    </g>
                ))}
            </svg>
        </div>
    );
}

// Aggregate grid: one row per account (Balance / Revenue / Expense)
export function AggregateGrid({rows, periods, t0, range = "all", measure = ""}) {
    const idx = pickIdx(periods.length, range), shown = measure ? rows.filter((r) => r.accountId === measure) : rows;
    return (
        <div className="data-table-wrap">
            <table className="data-table forecast-table">
                <thead>
                <tr>
                    <th className="sticky-col">Account ID</th>
                    <th className="sticky-col second">Measure</th>
                    <PeriodHead periods={periods} idx={idx} t0={t0}/></tr>
                </thead>
                <tbody>{shown.map((r) => (
                    <tr key={r.accountId}>
                        <td className="sticky-col code">{r.accountId}</td>
                        <td className="sticky-col second">{measureName(r.accountId, r.measure)}</td>
                        {idx.map((i) => <td key={i} className="number">{money(r.values[i])}</td>)}</tr>
                ))}</tbody>
            </table>
        </div>
    );
}
