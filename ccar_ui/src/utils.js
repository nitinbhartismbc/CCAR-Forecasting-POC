export const measures = [
    {id: "210001", name: "Balance", color: "#3478c5"},
    {id: "400001", name: "Revenue", color: "#13998a"},
    {id: "500001", name: "Expense", color: "#d58b36"},
];

export const measureName = (id, fallback) =>
    measures.find((m) => m.id === String(id))?.name ??
    fallback ??
    String(id);

export const money = (n) =>
    n == null
        ? "—"
        : Number(n).toLocaleString("en-US", {
            maximumFractionDigits: 0,
        });

const MON = [
    "Jan",
    "Feb",
    "Mar",
    "Apr",
    "May",
    "Jun",
    "Jul",
    "Aug",
    "Sep",
    "Oct",
    "Nov",
    "Dec",
];

const parse = (s) => {
    const [y, m, d] = String(s)
        .slice(0, 10)
        .split("-")
        .map(Number);

    return {y, m, d};
};

// T0 must be a quarter-end date (31 Mar, 30 Jun, 30 Sep, 31 Dec).
export const isQuarterEnd = (s) => {
    const {y, m, d} = parse(s);

    return (
        m % 3 === 0 &&
        d === new Date(Date.UTC(y, m, 0)).getUTCDate()
    );
};

export const fmtDate = (s) => {
    const {y, m, d} = parse(s);

    return `${d}-${MON[m - 1]}-${String(y).slice(2)}`;
};

export const shiftMonth = (t0, n) => {
    const {y, m} = parse(t0);

    const i = y * 12 + (m - 1) + n;

    return {
        y: Math.floor(i / 12),
        m: (i % 12) + 1,
    };
};

export const monthLabel = (t0, n) => {
    const {y, m} = shiftMonth(t0, n);

    return `${MON[m - 1]}-${String(y).slice(2)}`;
};

export const monthKey = (t0, n) => {
    const {y, m} = shiftMonth(t0, n);

    return `${y}-${String(m).padStart(2, "0")}`;
};

export const fmtMonthKey = (k) => {
    const [y, m] = k.split("-");

    return `${MON[Number(m) - 1]}-${y.slice(2)}`;
};

// "T0" -> 0, "T+3" -> 3, "T-2" -> -2
// Each period represents one month.
export const periodMonth = (t0, p) =>
    monthLabel(t0, Number(p.slice(1)));

export const defaultPeriods = Array.from(
    {length: 37},
    (_, i) => (i ? `T+${i}` : "T0")
);

export const pickIdx = (len, range) => {
    const r = (a, b) =>
        Array.from(
            {length: b - a + 1},
            (_, i) => a + i
        );

    return range === "first12"
        ? r(0, Math.min(12, len - 1))
        : range === "last12"
            ? r(Math.max(0, len - 12), len - 1)
            : r(0, len - 1);
};

// BU / GEO / OP_CODE scope -> query parameter expected by backend
export const scopeParams = (s) => ({
    [
        {
            BU: "buId",
            GEO: "geoId",
            OP_CODE: "opCodeId",
        }[s.kind]
        ]: s.id,
});