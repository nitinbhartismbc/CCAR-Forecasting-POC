package com.forecast.ccar.service;

import com.ccar.common.error.CcarException;
import com.ccar.common.error.ErrorCode;
import com.forecast.ccar.model.HierarchyDefinition;
import com.forecast.ccar.model.HierarchyInfo;
import com.forecast.ccar.model.HierarchyNode;
import com.iris.kdb.service.KdbTemplate;
import com.kx.c;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Loads ONE hierarchy (BU, GEO, ...) from its KDB table, builds a tree of {@link HierarchyNode}s and keeps it
 * cached in memory. {@link HierarchyService} holds one cache per hierarchy type.
 * <p>
 * Table layout (names from the {@link HierarchyDefinition}): {@code <P>_ID, <P>_NAME, LEVEL_ID,
 * <P>_ID_LEVEL1 .. <P>_ID_LEVELn}. Each row carries the full path from the root, so the parent of a level-N node
 * is the value in {@code <P>_ID_LEVEL(N-1)}. Rows are kept in table order, which determines sibling order.
 * <p>
 * The cached tree is published as an immutable snapshot (children lists are unmodifiable), so it is safe to read
 * concurrently and can be swapped atomically via {@link #reload()}.
 * <p>
 * Error handling:
 * <ul>
 *   <li>KDB failures propagate as the starter's {@code KdbException}s (mapped to 503/504/502).</li>
 *   <li>Bad table content becomes {@link ErrorCode#HIERARCHY_INVALID} (500); the detail is generic, the row-level
 *       cause is logged.</li>
 *   <li>While nothing is cached and the last load failed less than {@code retryIntervalMs} ago, requests fail fast
 *       with {@link ErrorCode#HIERARCHY_UNAVAILABLE} (503 + Retry-After) instead of each one queueing on the lock
 *       and hammering a KDB that is down.</li>
 *   <li>A failed reload keeps serving the previous snapshot.</li>
 * </ul>
 */
class HierarchyCache {

    private static final Logger log = LoggerFactory.getLogger(HierarchyCache.class);

    private final KdbTemplate kdbTemplate;
    private final HierarchyDefinition definition;
    private final long retryIntervalMs;
    private volatile Snapshot snapshot;
    /** Epoch millis before which no lazy (request-triggered) load is attempted; 0 = no back-off active. */
    private volatile long nextRetryAt;

    private record Snapshot(HierarchyNode root, Map<Long, HierarchyNode> byId, Instant loadedAt) {
    }

    HierarchyCache(KdbTemplate kdbTemplate, HierarchyDefinition definition, long retryIntervalMs) {
        this.kdbTemplate = kdbTemplate;
        this.definition = definition;
        this.retryIntervalMs = retryIntervalMs;
    }

    HierarchyDefinition definition() {
        return definition;
    }

    /** Tree from the root, limited to {@code depth} levels below it (null = full tree). Do not mutate. */
    HierarchyNode getHierarchy(Integer depth) {
        HierarchyNode root = snapshot().root();
        return depth == null ? root : root.copy(depth);
    }

    /** Subtree rooted at {@code id}, limited to {@code depth} levels below it (null = full subtree). */
    Optional<HierarchyNode> getNode(long id, Integer depth) {
        HierarchyNode node = snapshot().byId().get(id);
        if (node == null) {
            return Optional.empty();
        }
        return Optional.of(depth == null ? node : node.copy(depth));
    }

    /** Direct children of {@code id}, without grandchildren (childCount tells the UI if they are expandable). */
    Optional<List<HierarchyNode>> getChildren(long id) {
        HierarchyNode node = snapshot().byId().get(id);
        if (node == null) {
            return Optional.empty();
        }
        return Optional.of(node.getChildren().stream().map(c -> c.copy(0)).toList());
    }

    int size() {
        return snapshot().byId().size();
    }

    /** Status without triggering a load. */
    HierarchyInfo info() {
        Snapshot s = snapshot;
        return s == null
                ? new HierarchyInfo(definition.type(), definition.table(), false, 0, null)
                : new HierarchyInfo(definition.type(), definition.table(), true, s.byId().size(), s.loadedAt());
    }

    /**
     * Re-queries the KDB table and atomically replaces the cached tree. On failure the previous tree (if any)
     * stays in place and the lazy-load back-off is armed.
     */
    synchronized void reload() {
        try {
            doReload();
            nextRetryAt = 0;
        } catch (RuntimeException e) {
            nextRetryAt = System.currentTimeMillis() + retryIntervalMs;
            throw e;
        }
    }

    private void doReload() {
        String type = definition.type();
        String table = definition.table();
        log.info("Loading {} hierarchy from KDB table {}", type, table);
        long start = System.currentTimeMillis();

        // 0! unkeys the result in case the table is keyed, so we always get a c.Flip back.
        // KdbException (KDB down / busy / timeout / q error) propagates unchanged.
        Object result = kdbTemplate.execute("0!select from " + table);

        Map<Long, HierarchyNode> byId;
        HierarchyNode root;
        try {
            if (!(result instanceof c.Flip flip)) {
                throw new IllegalStateException("Unexpected KDB result for table " + table + ": "
                        + (result == null ? "null" : result.getClass().getName()));
            }
            byId = readNodes(flip, definition);
            root = link(byId, definition);
        } catch (IllegalStateException e) {
            throw new CcarException(ErrorCode.HIERARCHY_INVALID,
                    "The " + type + " hierarchy data is invalid and could not be loaded.", e)
                    .with("hierarchyType", type);
        }
        snapshot = new Snapshot(root, Collections.unmodifiableMap(byId), Instant.now());

        log.info("{} hierarchy loaded: {} node(s), root {} ('{}') in {} ms",
                type, byId.size(), root.getId(), root.getName(), System.currentTimeMillis() - start);
    }

    private Snapshot snapshot() {
        Snapshot s = snapshot;
        if (s != null) {
            return s;
        }
        synchronized (this) {
            if (snapshot == null) {
                long waitMs = nextRetryAt - System.currentTimeMillis();
                if (waitMs > 0) {
                    long retryAfter = Math.max(1, (waitMs + 999) / 1000);
                    throw new CcarException(ErrorCode.HIERARCHY_UNAVAILABLE,
                            "The " + definition.type() + " hierarchy is not loaded yet because the last load failed. "
                                    + "Retry in " + retryAfter + " s.", null, retryAfter)
                            .with("hierarchyType", definition.type());
                }
                reload();
            }
            return snapshot;
        }
    }

    /** Converts the KDB table into nodes (insertion order = table order), recording each node's parent id. */
    static Map<Long, HierarchyNode> readNodes(c.Flip flip, HierarchyDefinition def) {
        Map<String, Object> columns = new HashMap<>();
        for (int i = 0; i < flip.x.length; i++) {
            columns.put(flip.x[i].toUpperCase(Locale.ROOT), flip.y[i]);
        }
        Object ids = requireColumn(columns, def.idColumn(), def);
        Object names = requireColumn(columns, def.nameColumn(), def);
        Object levels = requireColumn(columns, def.levelColumn(), def);

        int rowCount = Array.getLength(ids);
        if (rowCount == 0) {
            throw new IllegalStateException(def.type() + " hierarchy table " + def.table() + " is empty");
        }

        Map<Long, HierarchyNode> byId = new LinkedHashMap<>(rowCount * 2);
        for (int row = 0; row < rowCount; row++) {
            try {
                long id = requireLong(c.at(ids, row), def.idColumn());
                String name = toText(c.at(names, row));
                int levelId = Math.toIntExact(requireLong(c.at(levels, row), def.levelColumn()));
                Long parentId = null;
                if (levelId > 1) {
                    String parentCol = def.levelColumnPrefix() + (levelId - 1);
                    parentId = requireLong(c.at(requireColumn(columns, parentCol, def), row), parentCol);
                }

                HierarchyNode previous = byId.put(id, new HierarchyNode(id, name, levelId, parentId));
                if (previous != null) {
                    log.warn("Duplicate {} id {} at row {}, previous entry ('{}') overwritten",
                            def.type(), id, row, previous.getName());
                }
            } catch (RuntimeException e) {
                throw new IllegalStateException(
                        "Invalid " + def.type() + " hierarchy row " + row + ": " + e.getMessage(), e);
            }
        }
        return byId;
    }

    private static Object requireColumn(Map<String, Object> columns, String name, HierarchyDefinition def) {
        Object column = columns.get(name);
        if (column == null) {
            throw new IllegalStateException(def.type() + " hierarchy table " + def.table() + " has no column " + name);
        }
        return column;
    }

    /** Accepts any KDB integral type (short/int/long) and rejects nulls. */
    private static long requireLong(Object value, String column) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        throw new IllegalStateException(column + " is " + (value == null ? "null" : "not numeric: " + value));
    }

    /** Names may be a symbol (String) or a q string (char[]). */
    private static String toText(Object value) {
        if (value == null) {
            return "";
        }
        return (value instanceof char[] chars ? new String(chars) : value.toString()).trim();
    }

    /** Attaches every node to its parent, freezes children lists, and returns the single root. */
    static HierarchyNode link(Map<Long, HierarchyNode> byId, HierarchyDefinition def) {
        List<HierarchyNode> roots = new ArrayList<>();
        int orphans = 0;

        for (HierarchyNode node : byId.values()) {
            if (node.getParentId() == null) {
                roots.add(node);
                continue;
            }
            HierarchyNode parent = byId.get(node.getParentId());
            if (parent == null) {
                orphans++;
                log.warn("Parent {} {} not found for node {} ('{}'); node not attached",
                        def.type(), node.getParentId(), node.getId(), node.getName());
            } else {
                parent.getChildren().add(node);
            }
        }

        for (HierarchyNode node : byId.values()) {
            node.setChildCount(node.getChildren().size());
            node.setChildren(List.copyOf(node.getChildren()));
        }

        if (roots.isEmpty()) {
            throw new IllegalStateException(def.type() + " hierarchy has no root (no row with "
                    + def.levelColumn() + " = 1)");
        }
        if (roots.size() > 1) {
            log.warn("{} {} root nodes found, using the first: {} ('{}')",
                    roots.size(), def.type(), roots.get(0).getId(), roots.get(0).getName());
        }
        if (orphans > 0) {
            log.warn("{} {} node(s) could not be attached to a parent", orphans, def.type());
        }
        return roots.get(0);
    }
}
