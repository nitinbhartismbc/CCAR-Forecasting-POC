import {useEffect, useMemo, useState} from "react";
import {messageFor} from "../api/errors.js";

function TreeNode({node, kidsOf, navId, onSelect, expanded, toggle, depth = 0}) {
    const kids = kidsOf[node.id] || [], open = expanded.has(node.id);
    return (
        <div>
            <button className={`tree-node ${navId === node.id ? "selected" : ""}`}
                    style={{paddingLeft: `${10 + depth * 14}px`}} onClick={() => onSelect(node)} title={node.name}>
                {kids.length ? <span className="tree-toggle" onClick={(e) => {
                    e.stopPropagation();
                    toggle(node.id);
                }}>{open ? "▾" : "▸"}</span> : <span className="tree-toggle leaf">·</span>}
                <span className="tree-label">{node.name}</span><span className="level-tag">L{node.level}</span>
            </button>
            {open && kids.map((c) => <TreeNode key={c.id} node={c} kidsOf={kidsOf} navId={navId} onSelect={onSelect}
                                               expanded={expanded} toggle={toggle} depth={depth + 1}/>)}
        </div>
    );
}

export default function Sidebar({page, setPage, hier, switchHier, nav, scope, onSelect, mock}) {
    const [expanded, setExpanded] = useState(new Set());
    const [search, setSearch] = useState("");
    const {kidsOf, byId, roots, nodes} = useMemo(() => {
        const nodes = nav.data || [], kidsOf = {}, byId = {}, roots = [];
        nodes.forEach((n) => {
            byId[n.id] = n;
            if (n.parentId == null) roots.push(n); else (kidsOf[n.parentId] ||= []).push(n);
        });
        return {kidsOf, byId, roots, nodes};
    }, [nav.data]);

    const select = (n) => {
        const trail = [], open = [n.id];
        for (let p = byId[n.parentId]; p; p = byId[p.parentId]) {
            trail.unshift(p.name);
            open.push(p.id);
        }
        setExpanded((prev) => new Set([...prev, ...open]));
        onSelect({id: n.id, name: n.name, level: n.level, kind: hier, childCount: n.childCount, trail, navId: n.id});
    };
    // Select the top node as soon as a hierarchy has loaded
    useEffect(() => {
        if (!scope && roots.length) select(roots[0]);
    }, [roots, scope]); // eslint-disable-line react-hooks/exhaustive-deps

    const term = search.trim().toLowerCase();
    const hits = term ? nodes.filter((n) => n.name.toLowerCase().includes(term)) : [];
    const toggle = (id) => setExpanded((p) => {
        const x = new Set(p);
        x.has(id) ? x.delete(id) : x.add(id);
        return x;
    });
    const items = [["forecast", "▥", "Forecast"], ["actuals", "▤", "Actual Data"], ["comparison", "⇄", "Comparison"]];

    return (
        <aside className="sidebar">
            <div className="side-section-label nav-label">WORKSPACE</div>
            <nav className="primary-nav">
                {items.map(([id, icon, label]) => <button key={id}
                                                          className={page === id ? "nav-item active" : "nav-item"}
                                                          onClick={() => setPage(id)}><span
                    className="nav-icon">{icon}</span>{label}</button>)}
            </nav>
            <div className="tree-section">
                <div className="seg" role="group" aria-label="Hierarchy">
                    <button className={hier === "BU" ? "on" : ""}
                            onClick={() => hier !== "BU" && switchHier("BU")}>Business unit
                    </button>
                    <button className={hier === "GEO" ? "on" : ""}
                            onClick={() => hier !== "GEO" && switchHier("GEO")}>Geography
                    </button>
                </div>
                <div className="tree-search"><span>⌕</span><input value={search}
                                                                  onChange={(e) => setSearch(e.target.value)}
                                                                  placeholder="Find in hierarchy"
                                                                  aria-label="Find in hierarchy"/>{search &&
                    <button onClick={() => setSearch("")} aria-label="Clear search">×</button>}</div>
                <div className="tree-list">
                    {nav.loading && <div className="tree-hint">Loading hierarchy…</div>}
                    {nav.error &&
                        <div className="tree-hint error" role="alert">Couldn’t load the hierarchy. {messageFor(nav.error)}
                            {nav.error.correlationId && <small> Ref: {nav.error.correlationId}</small>}{" "}
                            <button className="link-btn" onClick={nav.reload}>Retry</button>
                        </div>}
                    {!term && roots.map((r) => <TreeNode key={r.id} node={r} kidsOf={kidsOf} navId={scope?.navId}
                                                         onSelect={select} expanded={expanded} toggle={toggle}/>)}
                    {term && hits.slice(0, 60).map((n) => (
                        <button key={n.id}
                                className={`tree-node search-result ${scope?.navId === n.id ? "selected" : ""}`}
                                style={{paddingLeft: 10 + n.level * 8}} onClick={() => {
                            select(n);
                            setSearch("");
                        }}>
                            <span className="tree-toggle leaf">·</span><span className="tree-label">{n.name}</span><span
                            className="level-tag">L{n.level}</span>
                        </button>
                    ))}
                    {term && !hits.length && <div className="tree-hint">No match in the hierarchy.</div>}
                    {!nav.loading && !nav.error &&
                        <div className="tree-hint">Units and op codes are listed in the Details tab, not here.</div>}
                </div>
            </div>
            <div className="sidebar-bottom"><span
                className="status-dot"/>{mock ? "Mock services active" : "Backend: localhost:8081"}<span
                className="version">v0.2</span></div>
        </aside>
    );
}
