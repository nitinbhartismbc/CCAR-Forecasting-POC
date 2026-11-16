package com.forecast.ccar.service;

import com.ccar.common.error.CcarException;
import com.ccar.common.error.ErrorCode;
import com.forecast.ccar.config.HierarchyProperties;
import com.forecast.ccar.model.HierarchyDefinition;
import com.forecast.ccar.model.HierarchyInfo;
import com.iris.kdb.service.KdbTemplate;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.forecast.ccar.service.HierarchyCacheTest.ROWS;
import static com.forecast.ccar.service.HierarchyCacheTest.table;
import static org.junit.jupiter.api.Assertions.*;

/** Type resolution: configured types, convention-based types, unknown and malformed types. */
class HierarchyServiceTest {

    /** Fake KDB holding the given tables; records every query. */
    private static final class FakeKdb extends KdbTemplate {
        final Map<String, HierarchyDefinition> tables;
        final List<String> queries = new ArrayList<>();

        FakeKdb(HierarchyDefinition... defs) {
            super(null);
            Map<String, HierarchyDefinition> m = new java.util.HashMap<>();
            for (HierarchyDefinition d : defs) m.put(d.table(), d);
            this.tables = m;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T execute(String query) {
            queries.add(query);
            if (query.endsWith(" in tables[]")) {
                return (T) Boolean.valueOf(tables.containsKey(query.substring(1, query.indexOf(' '))));
            }
            String table = query.substring("0!select from ".length());
            return (T) table(tables.get(table), ROWS);
        }
    }

    private static HierarchyProperties props(boolean allowUnconfigured, String... types) {
        HierarchyProperties p = new HierarchyProperties();
        p.setAllowUnconfiguredTypes(allowUnconfigured);
        for (String t : types) {
            HierarchyProperties.Type cfg = new HierarchyProperties.Type();
            cfg.setTable(t.toUpperCase() + "_HIERARCHY");
            p.getTypes().put(t, cfg);
        }
        return p;
    }

    private static final HierarchyDefinition BU = HierarchyDefinition.of("BU", null, null);
    private static final HierarchyDefinition GEO = HierarchyDefinition.of("GEO", null, null);
    private static final HierarchyDefinition XYZ = HierarchyDefinition.of("XYZ", null, null);

    @Test
    void configuredTypesAreServedFromTheirOwnTable() {
        FakeKdb kdb = new FakeKdb(BU, GEO);
        HierarchyService service = new HierarchyService(kdb, props(true, "BU", "GEO"));

        assertEquals("ALL_BANK", service.getHierarchy("BU", null).getName());
        assertEquals(6, service.reload("GEO").nodes());
        assertTrue(kdb.queries.contains("0!select from BU_HIERARCHY"));
        assertTrue(kdb.queries.contains("0!select from GEO_HIERARCHY"));
        // configured types never need the existence check
        assertTrue(kdb.queries.stream().noneMatch(q -> q.endsWith("in tables[]")));
    }

    @Test
    void typeIsCaseInsensitiveAndSharesOneCache() {
        FakeKdb kdb = new FakeKdb(GEO);
        HierarchyService service = new HierarchyService(kdb, props(true, "geo"));

        service.getHierarchy("geo", 1);
        service.getHierarchy("Geo", 1);
        service.getNode("GEO", 3, 0);

        assertEquals(1, kdb.queries.size());
    }

    @Test
    void unconfiguredTypeIsServedByConventionWhenItsTableExists() {
        FakeKdb kdb = new FakeKdb(BU, XYZ);
        HierarchyService service = new HierarchyService(kdb, props(true, "BU"));

        assertEquals(3L, service.getChildren("xyz", 2).orElseThrow().get(0).getId());
        assertTrue(kdb.queries.contains("`XYZ_HIERARCHY in tables[]"));
        assertTrue(kdb.queries.contains("0!select from XYZ_HIERARCHY"));
        assertEquals(Set.of("BU", "XYZ"),
                Set.copyOf(service.getHierarchies().stream().map(HierarchyInfo::type).toList()));
    }

    @Test
    void unknownTypeIs404AndIsNotCached() {
        FakeKdb kdb = new FakeKdb(BU);
        HierarchyService service = new HierarchyService(kdb, props(true, "BU"));

        CcarException e = assertThrows(CcarException.class, () -> service.getHierarchy("NOPE", null));
        assertEquals(ErrorCode.HIERARCHY_NOT_FOUND, e.getErrorCode());
        assertEquals("NOPE", e.getProperties().get("hierarchyType"));
        assertEquals(List.of("BU"), service.getHierarchies().stream().map(HierarchyInfo::type).toList());
    }

    @Test
    void unconfiguredTypesCanBeDisabled() {
        FakeKdb kdb = new FakeKdb(BU, XYZ);
        HierarchyService service = new HierarchyService(kdb, props(false, "BU"));

        CcarException e = assertThrows(CcarException.class, () -> service.getHierarchy("XYZ", null));
        assertEquals(ErrorCode.HIERARCHY_NOT_FOUND, e.getErrorCode());
        assertTrue(kdb.queries.isEmpty());
    }

    @Test
    void malformedTypeIs400BeforeTouchingKdb() {
        FakeKdb kdb = new FakeKdb(BU);
        HierarchyService service = new HierarchyService(kdb, props(true, "BU"));

        for (String bad : List.of("", "1BU", "BU;delete", "BU HIERARCHY", "x".repeat(31))) {
            CcarException e = assertThrows(CcarException.class, () -> service.getHierarchy(bad, null), bad);
            assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode());
        }
        assertTrue(kdb.queries.isEmpty());
    }

    @Test
    void configuredTableAndColumnPrefixCanBeOverridden() {
        HierarchyProperties p = new HierarchyProperties();
        HierarchyProperties.Type cfg = new HierarchyProperties.Type();
        cfg.setTable("REGION_TREE");
        cfg.setColumnPrefix("GEO");
        p.getTypes().put("REGION", cfg);
        HierarchyDefinition region = new HierarchyDefinition("REGION", "REGION_TREE", "GEO_ID", "GEO_NAME",
                "LEVEL_ID", "GEO_ID_LEVEL");
        HierarchyService service = new HierarchyService(new FakeKdb(region), p);

        assertEquals(6, service.reload("REGION").nodes());
    }

    @Test
    void notLoadedTypesAreListedWithoutTouchingKdb() {
        FakeKdb kdb = new FakeKdb(BU, GEO);
        HierarchyService service = new HierarchyService(kdb, props(true, "BU", "GEO"));

        List<HierarchyInfo> infos = service.getHierarchies();
        assertEquals(List.of("BU", "GEO"), infos.stream().map(HierarchyInfo::type).toList());
        assertFalse(infos.get(0).loaded());
        assertTrue(kdb.queries.isEmpty());
    }
}
