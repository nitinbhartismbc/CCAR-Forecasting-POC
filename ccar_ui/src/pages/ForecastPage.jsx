import {useEffect, useState} from "react";
import {api} from "../api/client.js";
import {useAsync} from "../useAsync.js";
import {AggregateGrid, Async, MeasureCards, Pager, PeriodHead, TrendChart} from "../components/Common.jsx";
import {defaultPeriods, measureName, money, pickIdx, scopeParams} from "../utils.js";

const RangeSelect = ({range, setRange}) => (
    <select value={range} onChange={(e) => setRange(e.target.value)} aria-label="Period range">
        <option value="all">All periods (T0 to T+36)</option>
        <option value="first12">T0 to T+12</option>
        <option value="last12">T+25 to T+36</option>
    </select>
);

function AggregateTab({scope, t0, portfolio, measure, setMeasure, range, setRange}) {
    const st = useAsync((signal) => api.forecast({
        ...scopeParams(scope),
        portfolioId: portfolio,
        t0Date: t0
    }, {signal}), [scope.kind, scope.id, portfolio, t0]);
    return (
        <Async state={st} what="the forecast">{(d) => (
            <>
                <MeasureCards rows={d.rows} caption="Base value · T0" selected={measure} onPick={setMeasure}/>
                <TrendChart rows={d.rows} periods={d.periods} selected={measure} onSelect={setMeasure}/>
                <div className="table-card">
                    <div className="section-heading table-heading">
                        <div><h3>Aggregate forecast</h3><p>{scope.name} and everything below it, in USD</p></div>
                        <div className="table-actions"><RangeSelect range={range} setRange={setRange}/>
                            <button className="button secondary small" onClick={() => setMeasure("")}>Show all
                                measures
                            </button>
                        </div>
                    </div>
                    <AggregateGrid rows={d.rows} periods={d.periods} t0={t0} range={range} measure={measure}/>
                    <div className="table-footnote">Values are returned by the backend, already aggregated and converted
                        to USD.
                    </div>
                </div>
            </>
        )}</Async>
    );
}

function DetailsTab({scope, t0, portfolio, measure, setMeasure, range, setRange, onDrill}) {
    const [page, setPage] = useState(0), [size, setSize] = useState(25), [q, setQ] = useState(""), [search, setSearch] = useState("");
    useEffect(() => {
        const t = setTimeout(() => {
            setSearch(q.trim());
            setPage(0);
        }, 350);
        return () => clearTimeout(t);
    }, [q]);
    const st = useAsync((signal) => api.forecastChildren({
        ...scopeParams(scope),
        portfolioId: portfolio,
        t0Date: t0,
        page,
        size,
        search
    }, {signal}), [scope.kind, scope.id, portfolio, t0, page, size, search]);
    return (
        <div className="table-card">
            <div className="section-heading table-heading">
                <div><h3>Details</h3><p>Each entity directly below {scope.name}. Drill into one to see what sits under
                    it.</p></div>
                <div className="table-actions">
                    <input className="table-search" value={q} onChange={(e) => setQ(e.target.value)}
                           placeholder="Search name or ID" aria-label="Search entities"/>
                    <RangeSelect range={range} setRange={setRange}/>
                    <button className="button secondary small" onClick={() => setMeasure("")}>Show all measures</button>
                </div>
            </div>
            <Async state={st} what="the entity forecasts">{(d) => {
                const periods = d.periods || defaultPeriods, idx = pickIdx(periods.length, range);
                if (!d.items.length) return <div
                    className="state-box">{search ? `No entity matches “${search}”.` : "Nothing sits below this entity."}</div>;
                return (
                    <>
                        <div className="data-table-wrap">
                            <table className="data-table forecast-table detail">
                                <thead>
                                <tr>
                                    <th className="sticky-col">Entity</th>
                                    <th className="sticky-col second">Measure</th>
                                    <PeriodHead periods={periods} idx={idx} t0={t0}/></tr>
                                </thead>
                                <tbody>{d.items.map((it) => {
                                    const rows = measure ? it.rows.filter((r) => r.accountId === measure) : it.rows;
                                    return rows.map((r, ri) => (
                                        <tr key={`${it.id}-${r.accountId}`} className={ri === 0 ? "group-start" : ""}>
                                            {ri === 0 && (
                                                <td className="sticky-col entity" rowSpan={rows.length}>
                                                    <div className="entity-name" title={it.name}>{it.name}</div>
                                                    <div className="entity-meta"><span
                                                        className="code">{it.id}</span><span
                                                        className="level-tag">{it.kind === "OP_CODE" ? "Op code" : `L${it.level}`}</span>
                                                        {it.kind !== "OP_CODE" && it.childCount > 0 &&
                                                            <button className="link-btn"
                                                                    onClick={() => onDrill(it)}>Drill in</button>}</div>
                                                </td>
                                            )}
                                            <td className="sticky-col second">{measureName(r.accountId, r.measure)}</td>
                                            {idx.map((i) => <td key={i} className="number">{money(r.values[i])}</td>)}
                                        </tr>
                                    ));
                                })}</tbody>
                            </table>
                        </div>
                        <Pager page={d.page} size={d.size} total={d.totalElements} totalPages={d.totalPages}
                               onPage={setPage} onSize={(n) => {
                            setSize(n);
                            setPage(0);
                        }}/>
                    </>
                );
            }}</Async>
            <div className="table-footnote">Paged by the backend. Values are in USD.</div>
        </div>
    );
}

export default function ForecastPage({scope, t0, portfolio, onDrill}) {
    const [tab, setTab] = useState("aggregate"), [measure, setMeasure] = useState(""), [range, setRange] = useState("all");
    useEffect(() => setMeasure(""), [scope.kind, scope.id]);
    const hasDetails = scope.kind !== "OP_CODE" && scope.childCount > 0;
    const active = hasDetails ? tab : "aggregate";
    const shared = {scope, t0, portfolio, measure, setMeasure, range, setRange};
    return (
        <>
            <div className="tabs" role="tablist">
                <button role="tab" aria-selected={active === "aggregate"}
                        className={active === "aggregate" ? "tab on" : "tab"}
                        onClick={() => setTab("aggregate")}>Aggregate
                </button>
                <button role="tab" aria-selected={active === "details"}
                        className={active === "details" ? "tab on" : "tab"} disabled={!hasDetails}
                        onClick={() => setTab("details")}>
                    Details{hasDetails && <span className="tab-count">{scope.childCount}</span>}
                </button>
            </div>
            {active === "aggregate" ? <AggregateTab {...shared} /> :
                <DetailsTab key={`${scope.kind}-${scope.id}-${t0}-${portfolio}`} {...shared} onDrill={onDrill}/>}
        </>
    );
}
