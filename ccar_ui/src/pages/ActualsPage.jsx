import {api} from "../api/client.js";
import {useAsync} from "../useAsync.js";
import {AggregateGrid, Async, MeasureCards} from "../components/Common.jsx";
import {fmtDate, scopeParams} from "../utils.js";

export default function ActualsPage({scope, t0, portfolio}) {
    const st = useAsync(() => api.actuals({
        ...scopeParams(scope),
        portfolioId: portfolio,
        t0Date: t0
    }), [scope.kind, scope.id, portfolio, t0]);
    return (
        <Async state={st} what="actuals">{(d) => (
            <>
                <MeasureCards rows={d.rows} caption={`Actual at T0 · ${fmtDate(d.t0Date)}`}/>
                <div className="info-banner"><span className="info-icon">i</span>Actuals run backwards from T0: T-1 is
                    the month before T0, T-2 the month before that. Each column shows its calendar month.
                </div>
                <div className="table-card">
                    <div className="section-heading table-heading">
                        <div><h3>Actual data</h3><p>{scope.name} and everything below it, in USD</p></div>
                        <span className="pill">T0 {fmtDate(d.t0Date)}</span></div>
                    <AggregateGrid rows={d.rows} periods={d.periods} t0={t0}/>
                    <div className="table-footnote">Values are returned by the backend, already aggregated and converted
                        to USD.
                    </div>
                </div>
            </>
        )}</Async>
    );
}
