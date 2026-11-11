import {api} from "../api/client.js";
import {useAsync} from "../useAsync.js";
import {Async, MeasureCards} from "../components/Common.jsx";
import {fmtDate, fmtMonthKey, measureName, money, scopeParams} from "../utils.js";

export default function ComparisonPage({scope, t0, portfolio}) {
    const st = useAsync((signal) => api.comparison({
        ...scopeParams(scope),
        portfolioId: portfolio,
        t0Date: t0
    }, {signal}), [scope.kind, scope.id, portfolio, t0]);
    return (
        <Async state={st} what="the comparison">{(d) => {
            const latest = {};
            d.rows.forEach((r) => {
                latest[r.accountId] = {accountId: r.accountId, value: r.variance, month: r.month};
            });
            const last = Object.values(latest)[0]?.month;
            return (
                <>
                    <MeasureCards rows={Object.values(latest)}
                                  caption={last ? `Latest variance · ${fmtMonthKey(last)}` : "No actuals yet"} signed/>
                    <div className="info-banner"><span className="info-icon">i</span>Forecast made at
                        T0 {fmtDate(d.t0Date)} against actuals for the same calendar month. Variance = actual −
                        forecast.
                    </div>
                    <div className="table-card">
                        <div className="section-heading table-heading">
                            <div><h3>Actual vs forecast</h3><p>{scope.name} and everything below it</p></div>
                            <span className="pill">USD</span></div>
                        {!d.rows.length ? <div className="state-box">No actuals are available yet for the months
                            after {fmtDate(d.t0Date)}. Pick an earlier T0 date.</div> : (
                            <div className="data-table-wrap">
                                <table className="data-table">
                                    <thead>
                                    <tr>
                                        <th>Month</th>
                                        <th>Forecast period</th>
                                        <th>Measure</th>
                                        <th className="number">Forecast</th>
                                        <th className="number">Actual</th>
                                        <th className="number">Variance</th>
                                        <th className="number">Variance %</th>
                                    </tr>
                                    </thead>
                                    <tbody>{d.rows.map((r) => (
                                        <tr key={r.month + r.accountId}>
                                            <td>{fmtMonthKey(r.month)}</td>
                                            <td>{r.period}</td>
                                            <td>{measureName(r.accountId, r.measure)}</td>
                                            <td className="number">{money(r.forecast)}</td>
                                            <td className="number">{money(r.actual)}</td>
                                            <td className={`number ${r.variance < 0 ? "negative" : "positive"}`}>{r.variance > 0 ? "+" : ""}{money(r.variance)}</td>
                                            <td className={`number ${r.variancePct < 0 ? "negative" : "positive"}`}>{r.variancePct > 0 ? "+" : ""}{Number(r.variancePct).toFixed(1)}%</td>
                                        </tr>
                                    ))}</tbody>
                                </table>
                            </div>
                        )}
                        <div className="table-footnote">Variance is calculated by the backend; the UI only displays
                            it.
                        </div>
                    </div>
                </>
            );
        }}</Async>
    );
}
