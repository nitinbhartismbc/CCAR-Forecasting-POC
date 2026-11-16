package com.forecast.ccar.service;

import com.ccar.common.error.CcarException;
import com.ccar.common.error.ErrorCode;
import com.forecast.ccar.model.HierarchyDefinition;
import com.forecast.ccar.model.HierarchyNode;
import com.iris.kdb.exception.KdbConnectionException;
import com.iris.kdb.service.KdbTemplate;
import com.kx.c;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Building, caching and reloading ONE hierarchy - the same for every type, only the column names differ. */
class HierarchyCacheTest {

    static final long NULL = Long.MIN_VALUE; // KDB long null (0Nj)

    static final HierarchyDefinition BU = HierarchyDefinition.of("BU", "BU_HIERARCHY", null);
    static final HierarchyDefinition GEO = HierarchyDefinition.of("GEO", "GEO_HIERARCHY", null);

    /** Rows: ID, NAME, LEVEL_ID, ID_LEVEL1..4 (remaining levels are null). */
    static final Object[][] ROWS = {
            {1L, "ALL_BANK", 1L, 1L, NULL, NULL, NULL},
            {2L, "North America", 2L, 1L, 2L, NULL, NULL},
            {30L, "Europe", 2L, 1L, 30L, NULL, NULL},
            {3L, "United States", 3L, 1L, 2L, 3L, NULL},
            {5L, "Deposits", 4L, 1L, 2L, 3L, 5L},
            {4L, "Cards", 4L, 1L, 2L, 3L, 4L},
    };

    /** Builds a c.Flip shaped like a hierarchy table of {@code def} (names as symbols, ids as longs). */
    static c.Flip table(HierarchyDefinition def, Object[][] rows) {
        List<String> cols = new ArrayList<>(List.of(def.idColumn(), def.nameColumn(), def.levelColumn()));
        for (int l = 1; l <= 10; l++) {
            cols.add(def.levelColumnPrefix() + l);
        }
        Object[] data = new Object[cols.size()];
        for (int col = 0; col < cols.size(); col++) {
            if (col == 1) {
                String[] names = new String[rows.length];
                for (int r = 0; r < rows.length; r++) names[r] = (String) rows[r][1];
                data[col] = names;
            } else {
                long[] values = new long[rows.length];
                for (int r = 0; r < rows.length; r++) {
                    values[r] = col < rows[r].length ? (Long) rows[r][col] : NULL;
                }
                data[col] = values;
            }
        }
        return new c.Flip(cols.toArray(String[]::new), data);
    }

    private static HierarchyNode build(HierarchyDefinition def, c.Flip flip) {
        return HierarchyCache.link(HierarchyCache.readNodes(flip, def), def);
    }

    private static HierarchyCache cacheReturning(HierarchyDefinition def, Object result) {
        KdbTemplate kdb = new KdbTemplate(null) {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T execute(String query) {
                assertEquals("0!select from " + def.table(), query);
                return (T) result;
            }
        };
        return new HierarchyCache(kdb, def, 30_000);
    }

    @Test
    void buildsTreeInTableOrder() {
        HierarchyNode root = build(BU, table(BU, ROWS));

        assertEquals(1L, root.getId());
        assertNull(root.getParentId());
        assertEquals(List.of(2L, 30L), root.getChildren().stream().map(HierarchyNode::getId).toList());

        HierarchyNode us = root.getChildren().get(0).getChildren().get(0);
        assertEquals("United States", us.getName());
        // table order, not id order
        assertEquals(List.of(5L, 4L), us.getChildren().stream().map(HierarchyNode::getId).toList());
        assertEquals(3L, us.getChildren().get(0).getParentId());
    }

    @Test
    void sameLogicReadsGeoColumns() {
        HierarchyNode root = build(GEO, table(GEO, ROWS));

        assertEquals("ALL_BANK", root.getName());
        assertEquals(2, root.getChildCount());
    }

    @Test
    void bothColumnLayoutsAreNotInterchangeable() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> build(GEO, table(BU, ROWS)));
        assertTrue(e.getMessage().contains("GEO_ID"), e.getMessage());
    }

    @Test
    void acceptsCharArrayNames() {
        c.Flip flip = table(BU, ROWS);
        String[] symbols = (String[]) flip.y[1];
        Object[] strings = new Object[symbols.length];
        for (int i = 0; i < symbols.length; i++) strings[i] = symbols[i].toCharArray();
        flip.y[1] = strings;

        assertEquals("ALL_BANK", build(BU, flip).getName());
    }

    @Test
    void depthLimitedCopyKeepsChildCount() {
        HierarchyNode root = build(BU, table(BU, ROWS));

        HierarchyNode shallow = root.copy(1);
        HierarchyNode na = shallow.getChildren().get(0);
        assertTrue(na.getChildren().isEmpty());
        assertEquals(1, na.getChildCount());
        assertEquals(2, shallow.getChildCount());
    }

    @Test
    void cachedTreeIsImmutable() {
        HierarchyNode root = build(BU, table(BU, ROWS));
        assertThrows(UnsupportedOperationException.class, () -> root.getChildren().add(new HierarchyNode()));
    }

    @Test
    void rejectsRowWithMissingParent() {
        Object[][] bad = java.util.Arrays.copyOf(ROWS, ROWS.length + 1);
        bad[ROWS.length] = new Object[]{9L, "Broken", 3L, 1L, NULL, NULL, NULL};
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> build(GEO, table(GEO, bad)));
        assertTrue(e.getMessage().contains("row 6"), e.getMessage());
        assertTrue(e.getMessage().contains("GEO_ID_LEVEL2"), e.getMessage());
    }

    @Test
    void loadsFromKdb() {
        HierarchyCache cache = cacheReturning(GEO, table(GEO, ROWS));

        assertEquals("ALL_BANK", cache.getHierarchy(null).getName());
        assertEquals(6, cache.size());
        assertTrue(cache.getNode(3, 0).isPresent());
        assertEquals(3L, cache.getChildren(2).orElseThrow().get(0).getId());
        assertTrue(cache.getNode(-1, 0).isEmpty());
        assertTrue(cache.info().loaded());
        assertEquals("GEO_HIERARCHY", cache.info().table());
    }

    @Test
    void rejectsNonTableResult() {
        HierarchyCache cache = cacheReturning(BU, 42L);
        CcarException e = assertThrows(CcarException.class, cache::reload);
        assertEquals(ErrorCode.HIERARCHY_INVALID, e.getErrorCode());
        assertEquals("BU", e.getProperties().get("hierarchyType"));
        assertInstanceOf(IllegalStateException.class, e.getCause());
    }

    @Test
    void kdbFailurePropagatesThenFailsFastDuringBackoff() {
        AtomicInteger calls = new AtomicInteger();
        KdbTemplate kdb = new KdbTemplate(null) {
            @Override
            public <T> T execute(String query) {
                calls.incrementAndGet();
                throw new KdbConnectionException("Cannot connect to KDB");
            }
        };
        HierarchyCache cache = new HierarchyCache(kdb, GEO, 60_000);

        // first request hits KDB and surfaces the KDB failure (-> 503 KDB_UNAVAILABLE)
        assertThrows(KdbConnectionException.class, () -> cache.getHierarchy(null));
        // later requests fail fast without touching KDB (-> 503 HIERARCHY_UNAVAILABLE + Retry-After)
        CcarException e = assertThrows(CcarException.class, () -> cache.getHierarchy(null));
        assertEquals(ErrorCode.HIERARCHY_UNAVAILABLE, e.getErrorCode());
        assertEquals("GEO", e.getProperties().get("hierarchyType"));
        assertTrue(e.getRetryAfterSeconds() > 0 && e.getRetryAfterSeconds() <= 60);
        assertEquals(1, calls.get());
        assertFalse(cache.info().loaded());
    }

    @Test
    void failedReloadKeepsPreviousTree() {
        AtomicInteger calls = new AtomicInteger();
        KdbTemplate kdb = new KdbTemplate(null) {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T execute(String query) {
                if (calls.incrementAndGet() > 1) {
                    throw new KdbConnectionException("KDB went away");
                }
                return (T) table(BU, ROWS);
            }
        };
        HierarchyCache cache = new HierarchyCache(kdb, BU, 60_000);
        assertEquals(6, cache.size());

        assertThrows(KdbConnectionException.class, cache::reload);
        assertEquals("ALL_BANK", cache.getHierarchy(null).getName());
    }
}
