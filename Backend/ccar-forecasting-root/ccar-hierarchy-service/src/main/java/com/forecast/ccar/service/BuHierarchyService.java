package com.forecast.ccar.service;

import com.ccar.common.error.CcarException;
import com.ccar.common.error.ErrorCode;
import com.forecast.ccar.model.BuNode;
import com.iris.kdb.service.KdbTemplate;
import com.kx.c;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.lang.reflect.Array;
import java.time.Instant;
import java.util.*;

/**
 * Loads the BU hierarchy from the KDB table {@code BU_HIERARCHY} once, builds a tree of {@link BuNode}s
 * and keeps it cached in memory.
 * <p>
 * Table layout: {@code BU_ID, BU_NAME, LEVEL_ID, BU_ID_LEVEL1 .. BU_ID_LEVEL10}. Each row carries the
 * full path from the root, so the parent of a level-N node is the value in {@code BU_ID_LEVEL(N-1)}.
 * Rows are kept in table order, which determines sibling order in the tree.
 * <p>
 * The cached tree is published as an immutable snapshot (children lists are unmodifiable), so it is
 * safe to read concurrently and can be swapped atomically via {@link #reload()}.
 * <p>
 * Error handling:
 * <ul>
 *   <li>KDB failures propagate as the starter's {@code KdbException}s (mapped to 503/504/502).</li>
 *   <li>Bad table content becomes {@link ErrorCode#BU_HIERARCHY_INVALID} (500); the detail is generic, the
 *       row-level cause is logged.</li>
 *   <li>While nothing is cached and the last load failed less than {@code ccar.bu-hierarchy.retry-interval-ms}
 *       ago, requests fail fast with {@link ErrorCode#BU_HIERARCHY_UNAVAILABLE} (503 + Retry-After) instead of
 *       each one queueing on the lock and hammering a KDB that is down.</li>
 *   <li>A failed reload keeps serving the previous snapshot.</li>
 * </ul>
 */
@Service
public class BuHierarchyService {

    private static final Logger log = LoggerFactory.getLogger(BuHierarchyService.class);

    static final String COL_BU_ID = "BU_ID";
    static final String COL_BU_NAME = "BU_NAME";
    static final String COL_LEVEL_ID = "LEVEL_ID";
    static final String COL_LEVEL_PREFIX = "BU_ID_LEVEL";

    private final KdbTemplate kdbTemplate;
    private final String table;
    private final long retryIntervalMs;
    private volatile Snapshot snapshot;
    /** Epoch millis before which no lazy (request-triggered) load is attempted; 0 = no back-off active. */
    private volatile long nextRetryAt;

    private record Snapshot(BuNode root, Map<Long, BuNode> byId, Instant loadedAt) {
    }

    @Autowired
    public BuHierarchyService(KdbTemplate kdbTemplate,
                              @Value("${ccar.bu-hierarchy.table:BU_HIERARCHY}") String table,
                              @Value("${ccar.bu-hierarchy.retry-interval-ms:30000}") long retryIntervalMs) {
        if (!table.matches("[A-Za-z][A-Za-z0-9_.]*")) {
            throw new IllegalArgumentException("Invalid KDB table name for BU hierarchy: " + table);
        }
        this.kdbTemplate = kdbTemplate;
        this.table = table;
        this.retryIntervalMs = retryIntervalMs;
    }

    public BuHierarchyService(KdbTemplate kdbTemplate, String table) {
        this(kdbTemplate, table, 30_000);
    }

    /** Warm the cache on startup; a failure here is logged and retried on first request. */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        try {
            reload();
        } catch (RuntimeException e) {
            log.error("BU hierarchy could not be loaded at startup from KDB table {} (next attempt on first request "
                    + "after {} ms): {}", table, retryIntervalMs, e.toString(), e);
        }
    }

    /** Full cached tree. Do not mutate the returned object. */
    public BuNode getHierarchy() {
        return snapshot().root();
    }

    /** Tree from the root, limited to {@code depth} levels below it (null = full tree). */
    public BuNode getHierarchy(Integer depth) {
        BuNode root = getHierarchy();
        return depth == null ? root : root.copy(depth);
    }

    /** Subtree rooted at {@code buId}, limited to {@code depth} levels below it (null = full subtree). */
    public Optional<BuNode> getNode(long buId, Integer depth) {
        BuNode node = snapshot().byId().get(buId);
        if (node == null) {
            return Optional.empty();
        }
        return Optional.of(depth == null ? node : node.copy(depth));
    }

    /** Direct children of {@code buId}, without grandchildren (childCount tells the UI if they are expandable). */
    public Optional<List<BuNode>> getChildren(long buId) {
        BuNode node = snapshot().byId().get(buId);
        if (node == null) {
            return Optional.empty();
        }
        return Optional.of(node.getChildren().stream().map(c -> c.copy(0)).toList());
    }

    public int size() {
        return snapshot().byId().size();
    }

    public Instant loadedAt() {
        return snapshot().loadedAt();
    }

    /**
     * Re-queries the KDB table and atomically replaces the cached tree. On failure the previous tree (if any)
     * stays in place and the lazy-load back-off is armed.
     */
    public synchronized void reload() {
        try {
            doReload();
            nextRetryAt = 0;
        } catch (RuntimeException e) {
            nextRetryAt = System.currentTimeMillis() + retryIntervalMs;
            throw e;
        }
    }

    private void doReload() {
        log.info("Loading BU hierarchy from KDB table {}", table);
        long start = System.currentTimeMillis();

        // 0! unkeys the result in case BU_HIERARCHY is a keyed table, so we always get a c.Flip back.
        // KdbException (KDB down / busy / timeout / q error) propagates unchanged.
        Object result = kdbTemplate.execute("0!select from " + table);

        Map<Long, BuNode> byId;
        BuNode root;
        try {
            if (!(result instanceof c.Flip flip)) {
                throw new IllegalStateException("Unexpected KDB result for table " + table + ": "
                        + (result == null ? "null" : result.getClass().getName()));
            }
            byId = readNodes(flip);
            root = link(byId);
        } catch (IllegalStateException e) {
            throw new CcarException(ErrorCode.BU_HIERARCHY_INVALID,
                    "The BU hierarchy data is invalid and could not be loaded.", e);
        }
        snapshot = new Snapshot(root, Collections.unmodifiableMap(byId), Instant.now());

        log.info("BU hierarchy loaded: {} node(s), root {} ('{}') in {} ms",
                byId.size(), root.getId(), root.getName(), System.currentTimeMillis() - start);
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
                    throw new CcarException(ErrorCode.BU_HIERARCHY_UNAVAILABLE,
                            "The BU hierarchy is not loaded yet because the last load failed. Retry in "
                                    + retryAfter + " s.", null, retryAfter);
                }
                reload();
            }
            return snapshot;
        }
    }

    /** Converts the KDB table into nodes (insertion order = table order), recording each node's parent id. */
    static Map<Long, BuNode> readNodes(c.Flip flip) {
        Map<String, Object> columns = new HashMap<>();
        for (int i = 0; i < flip.x.length; i++) {
            columns.put(flip.x[i].toUpperCase(Locale.ROOT), flip.y[i]);
        }
        Object ids = requireColumn(columns, COL_BU_ID);
        Object names = requireColumn(columns, COL_BU_NAME);
        Object levels = requireColumn(columns, COL_LEVEL_ID);

        int rowCount = Array.getLength(ids);
        if (rowCount == 0) {
            throw new IllegalStateException("BU hierarchy table is empty");
        }

        Map<Long, BuNode> byId = new LinkedHashMap<>(rowCount * 2);
        for (int row = 0; row < rowCount; row++) {
            try {
                long buId = requireLong(c.at(ids, row), COL_BU_ID);
                String buName = toText(c.at(names, row));
                int levelId = Math.toIntExact(requireLong(c.at(levels, row), COL_LEVEL_ID));
                Long parentId = null;
                if (levelId > 1) {
                    String parentCol = COL_LEVEL_PREFIX + (levelId - 1);
                    parentId = requireLong(c.at(requireColumn(columns, parentCol), row), parentCol);
                }

                BuNode previous = byId.put(buId, new BuNode(buId, buName, levelId, parentId));
                if (previous != null) {
                    log.warn("Duplicate BU id {} at row {}, previous entry ('{}') overwritten",
                            buId, row, previous.getName());
                }
            } catch (RuntimeException e) {
                throw new IllegalStateException("Invalid BU hierarchy row " + row + ": " + e.getMessage(), e);
            }
        }
        return byId;
    }

    private static Object requireColumn(Map<String, Object> columns, String name) {
        Object column = columns.get(name);
        if (column == null) {
            throw new IllegalStateException("BU hierarchy table has no column " + name);
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

    /** BU_NAME may be a symbol (String) or a q string (char[]). */
    private static String toText(Object value) {
        if (value == null) {
            return "";
        }
        return (value instanceof char[] chars ? new String(chars) : value.toString()).trim();
    }

    /** Attaches every node to its parent, freezes children lists, and returns the single root. */
    static BuNode link(Map<Long, BuNode> byId) {
        List<BuNode> roots = new ArrayList<>();
        int orphans = 0;

        for (BuNode node : byId.values()) {
            if (node.getParentId() == null) {
                roots.add(node);
                continue;
            }
            BuNode parent = byId.get(node.getParentId());
            if (parent == null) {
                orphans++;
                log.warn("Parent BU {} not found for BU {} ('{}'); node not attached",
                        node.getParentId(), node.getId(), node.getName());
            } else {
                parent.getChildren().add(node);
            }
        }

        for (BuNode node : byId.values()) {
            node.setChildCount(node.getChildren().size());
            node.setChildren(List.copyOf(node.getChildren()));
        }

        if (roots.isEmpty()) {
            throw new IllegalStateException("BU hierarchy has no root (no row with LEVEL_ID = 1)");
        }
        if (roots.size() > 1) {
            log.warn("{} root nodes found, using the first: {} ('{}')",
                    roots.size(), roots.get(0).getId(), roots.get(0).getName());
        }
        if (orphans > 0) {
            log.warn("{} BU node(s) could not be attached to a parent", orphans);
        }
        return roots.get(0);
    }
}
