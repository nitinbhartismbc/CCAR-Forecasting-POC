package com.ccar.forecasting.hierarchy;

import com.ccar.common.error.CcarException;
import com.ccar.common.error.ErrorCode;
import com.ccar.forecasting.config.ForecastingProperties;
import com.ccar.forecasting.kdb.FlipReader;
import com.iris.kdb.service.KdbTemplate;
import com.kx.c;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Loads each configured hierarchy table from KDB once and caches it in memory (hierarchies change rarely, while
 * every data request needs node levels and children). Configured types are warmed at startup; a type that failed to
 * load is retried on its next request. {@link #reload(String)} swaps in a fresh snapshot; on failure the previous
 * snapshot keeps being served.
 */
@Component
public class HierarchyRepository {

    private static final Logger log = LoggerFactory.getLogger(HierarchyRepository.class);

    private final KdbTemplate kdbTemplate;
    private final Map<String, HierarchyDefinition> definitions;
    private final ConcurrentMap<String, Hierarchy> cache = new ConcurrentHashMap<>();

    public HierarchyRepository(KdbTemplate kdbTemplate, ForecastingProperties properties) {
        this.kdbTemplate = kdbTemplate;
        Map<String, HierarchyDefinition> defs = new LinkedHashMap<>();
        properties.getHierarchy().getTypes().forEach((type, cfg) -> {
            HierarchyDefinition def = HierarchyDefinition.of(type, cfg.getTable(), cfg.getColumnPrefix(),
                    cfg.getFactColumn());
            defs.put(def.type(), def);
        });
        this.definitions = Collections.unmodifiableMap(defs);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        for (String type : definitions.keySet()) {
            try {
                reload(type);
            } catch (RuntimeException e) {
                log.error("{} hierarchy could not be loaded at startup (retried on first request): {}",
                        type, e.toString());
            }
        }
    }

    /**
     * The cached hierarchy of {@code type}, loaded on first use.
     *
     * @throws CcarException 400 INVALID_PARAMETER for a malformed type, 404 HIERARCHY_NOT_FOUND for an unsupported
     *                       one, 500 HIERARCHY_INVALID for a broken table; KdbExceptions for KDB failures
     */
    public Hierarchy get(String type) {
        HierarchyDefinition def = definition(type);
        Hierarchy h = cache.get(def.type());
        if (h != null) {
            return h;
        }
        synchronized (def) {
            h = cache.get(def.type());
            return h != null ? h : load(def);
        }
    }

    /** Re-reads the hierarchy table into the cache. */
    public Hierarchy reload(String type) {
        HierarchyDefinition def = definition(type);
        synchronized (def) {
            return load(def);
        }
    }

    HierarchyDefinition definition(String type) {
        String t = HierarchyDefinition.normalizeType(type);
        HierarchyDefinition def = definitions.get(t);
        if (def == null) {
            throw new CcarException(ErrorCode.HIERARCHY_NOT_FOUND,
                    "Hierarchy type " + t + " is not supported. Supported types: " + definitions.keySet())
                    .with("hierarchyType", t);
        }
        return def;
    }

    private Hierarchy load(HierarchyDefinition def) {
        long start = System.currentTimeMillis();
        // 0! unkeys a keyed table so the result is always a c.Flip; KdbExceptions propagate (-> 502/503/504)
        c.Flip flip = kdbTemplate.execute("0!select from " + def.table(), c.Flip.class);
        Hierarchy h;
        try {
            h = Hierarchy.build(def, readRows(flip, def));
        } catch (IllegalStateException e) {
            log.error("{} hierarchy table {} is invalid: {}", def.type(), def.table(), e.getMessage());
            throw new CcarException(ErrorCode.HIERARCHY_INVALID,
                    "The " + def.type() + " hierarchy data is invalid and could not be loaded.", e)
                    .with("hierarchyType", def.type());
        }
        cache.put(def.type(), h);
        log.info("{} hierarchy loaded from {}: {} node(s), root {} ('{}') in {} ms", def.type(), def.table(),
                h.size(), h.root().id(), h.root().name(), System.currentTimeMillis() - start);
        return h;
    }

    /** Hierarchy table -> rows; the parent of a level-N node is its {@code <P>_ID_LEVEL(N-1)} value. */
    static List<Hierarchy.RawNode> readRows(c.Flip flip, HierarchyDefinition def) {
        FlipReader table = new FlipReader(flip);
        Object ids = table.column(def.idColumn());
        Object names = table.column(def.nameColumn());
        Object levels = table.column(def.levelColumn());

        int rowCount = table.rowCount();
        if (rowCount == 0) {
            throw new IllegalStateException("table " + def.table() + " is empty");
        }
        List<Hierarchy.RawNode> rows = new ArrayList<>(rowCount);
        for (int row = 0; row < rowCount; row++) {
            try {
                long id = FlipReader.requireLong(c.at(ids, row), def.idColumn());
                int level = Math.toIntExact(FlipReader.requireLong(c.at(levels, row), def.levelColumn()));
                Long parentId = null;
                if (level > 1) {
                    String parentCol = def.levelColumn(level - 1);
                    parentId = FlipReader.requireLong(c.at(table.column(parentCol), row), parentCol);
                }
                rows.add(new Hierarchy.RawNode(id, FlipReader.text(c.at(names, row)), level, parentId));
            } catch (RuntimeException e) {
                throw new IllegalStateException("row " + row + ": " + e.getMessage(), e);
            }
        }
        return rows;
    }
}
