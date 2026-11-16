package com.forecast.ccar.service;

import com.ccar.common.error.CcarException;
import com.ccar.common.error.ErrorCode;
import com.forecast.ccar.model.BuNode;
import com.iris.kdb.exception.KdbConnectionException;
import com.iris.kdb.service.KdbTemplate;
import com.kx.c;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BuHierarchyServiceTest {

    private static final long NULL = Long.MIN_VALUE; // KDB long null (0Nj)

    /** Rows: BU_ID, BU_NAME, LEVEL_ID, BU_ID_LEVEL1..4 (remaining levels are null). */
    private static final Object[][] ROWS = {
            {1L, "ALL_BANK", 1L, 1L, NULL, NULL, NULL},
            {2L, "North America", 2L, 1L, 2L, NULL, NULL},
            {30L, "Europe", 2L, 1L, 30L, NULL, NULL},
            {3L, "United States", 3L, 1L, 2L, 3L, NULL},
            {5L, "Deposits", 4L, 1L, 2L, 3L, 5L},
            {4L, "Cards", 4L, 1L, 2L, 3L, 4L},
    };

    /** Builds a c.Flip shaped like the BU_HIERARCHY table (BU_NAME as symbol, ids as longs). */
    static c.Flip table(Object[][] rows) {
        List<String> cols = new ArrayList<>(List.of("BU_ID", "BU_NAME", "LEVEL_ID"));
        for (int l = 1; l <= 10; l++) {
            cols.add("BU_ID_LEVEL" + l);
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

    private static BuNode build(c.Flip flip) {
        return BuHierarchyService.link(BuHierarchyService.readNodes(flip));
    }

    private static BuHierarchyService serviceReturning(Object result) {
        KdbTemplate kdb = new KdbTemplate(null) {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T execute(String query) {
                assertEquals("0!select from BU_HIERARCHY", query);
                return (T) result;
            }
        };
        return new BuHierarchyService(kdb, "BU_HIERARCHY");
    }

    @Test
    void buildsTreeInTableOrder() {
        BuNode root = build(table(ROWS));

        assertEquals(1L, root.getId());
        assertNull(root.getParentId());
        assertEquals(List.of(2L, 30L), root.getChildren().stream().map(BuNode::getId).toList());

        BuNode us = root.getChildren().get(0).getChildren().get(0);
        assertEquals("United States", us.getName());
        // table order, not id order
        assertEquals(List.of(5L, 4L), us.getChildren().stream().map(BuNode::getId).toList());
        assertEquals(3L, us.getChildren().get(0).getParentId());
    }

    @Test
    void acceptsCharArrayNames() {
        c.Flip flip = table(ROWS);
        String[] symbols = (String[]) flip.y[1];
        Object[] strings = new Object[symbols.length];
        for (int i = 0; i < symbols.length; i++) strings[i] = symbols[i].toCharArray();
        flip.y[1] = strings;

        assertEquals("ALL_BANK", build(flip).getName());
    }

    @Test
    void depthLimitedCopyKeepsChildCount() {
        BuNode root = build(table(ROWS));

        BuNode shallow = root.copy(1);
        BuNode na = shallow.getChildren().get(0);
        assertTrue(na.getChildren().isEmpty());
        assertEquals(1, na.getChildCount());
        assertEquals(2, shallow.getChildCount());
    }

    @Test
    void cachedTreeIsImmutable() {
        BuNode root = build(table(ROWS));
        assertThrows(UnsupportedOperationException.class, () -> root.getChildren().add(new BuNode()));
    }

    @Test
    void rejectsRowWithMissingParent() {
        Object[][] bad = java.util.Arrays.copyOf(ROWS, ROWS.length + 1);
        bad[ROWS.length] = new Object[]{9L, "Broken", 3L, 1L, NULL, NULL, NULL};
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> build(table(bad)));
        assertTrue(e.getMessage().contains("row 6"), e.getMessage());
        assertTrue(e.getMessage().contains("BU_ID_LEVEL2"), e.getMessage());
    }

    @Test
    void loadsFromKdb() {
        BuHierarchyService service = serviceReturning(table(ROWS));

        assertEquals("ALL_BANK", service.getHierarchy().getName());
        assertEquals(6, service.size());
        assertTrue(service.getNode(3, 0).isPresent());
        assertEquals(3L, service.getChildren(2).orElseThrow().get(0).getId());
        assertTrue(service.getNode(-1, 0).isEmpty());
    }

    @Test
    void rejectsNonTableResult() {
        BuHierarchyService service = serviceReturning(42L);
        CcarException e = assertThrows(CcarException.class, service::reload);
        assertEquals(ErrorCode.BU_HIERARCHY_INVALID, e.getErrorCode());
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
        BuHierarchyService service = new BuHierarchyService(kdb, "BU_HIERARCHY", 60_000);

        // first request hits KDB and surfaces the KDB failure (-> 503 KDB_UNAVAILABLE)
        assertThrows(KdbConnectionException.class, service::getHierarchy);
        // later requests fail fast without touching KDB (-> 503 BU_HIERARCHY_UNAVAILABLE + Retry-After)
        CcarException e = assertThrows(CcarException.class, service::getHierarchy);
        assertEquals(ErrorCode.BU_HIERARCHY_UNAVAILABLE, e.getErrorCode());
        assertTrue(e.getRetryAfterSeconds() > 0 && e.getRetryAfterSeconds() <= 60);
        assertEquals(1, calls.get());
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
                return (T) table(ROWS);
            }
        };
        BuHierarchyService service = new BuHierarchyService(kdb, "BU_HIERARCHY", 60_000);
        assertEquals(6, service.size());

        assertThrows(KdbConnectionException.class, service::reload);
        assertEquals("ALL_BANK", service.getHierarchy().getName());
    }
}
