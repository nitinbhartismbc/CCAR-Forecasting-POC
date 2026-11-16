package com.forecast.ccar.service;

import com.ccar.common.error.CcarException;
import com.ccar.common.error.ErrorCode;
import com.forecast.ccar.config.HierarchyProperties;
import com.forecast.ccar.model.HierarchyDefinition;
import com.forecast.ccar.model.HierarchyInfo;
import com.forecast.ccar.model.HierarchyNode;
import com.iris.kdb.service.KdbTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Generic hierarchy service: one cached tree per hierarchy type (BU, GEO, ...), all built the same way by a
 * {@link HierarchyCache}. The type comes from the request; how it maps to a KDB table is a
 * {@link HierarchyDefinition}:
 * <ul>
 *   <li>types under {@code ccar.hierarchy.types.*} are loaded at startup;</li>
 *   <li>any other type {@code XYZ} is accepted on first request when {@code ccar.hierarchy.allow-unconfigured-types}
 *       is true (default) and the table {@code XYZ_HIERARCHY} exists in KDB - no code or config change needed;</li>
 *   <li>an unknown type (no such table) is 404 {@link ErrorCode#HIERARCHY_NOT_FOUND}; a malformed type is
 *       400 INVALID_PARAMETER.</li>
 * </ul>
 */
@Service
public class HierarchyService {

    private static final Logger log = LoggerFactory.getLogger(HierarchyService.class);

    private final KdbTemplate kdbTemplate;
    private final long retryIntervalMs;
    private final boolean allowUnconfiguredTypes;
    /** Configured types in declaration order. */
    private final Map<String, HierarchyDefinition> configured;
    private final ConcurrentMap<String, HierarchyCache> caches = new ConcurrentHashMap<>();

    public HierarchyService(KdbTemplate kdbTemplate, HierarchyProperties properties) {
        this.kdbTemplate = kdbTemplate;
        this.retryIntervalMs = properties.getRetryIntervalMs();
        this.allowUnconfiguredTypes = properties.isAllowUnconfiguredTypes();

        Map<String, HierarchyDefinition> defs = new LinkedHashMap<>();
        properties.getTypes().forEach((type, cfg) -> {
            HierarchyDefinition def = HierarchyDefinition.of(type, cfg.getTable(), cfg.getColumnPrefix());
            defs.put(def.type(), def);
        });
        this.configured = Collections.unmodifiableMap(defs);
    }

    /** Warm the configured hierarchies on startup; a failure is logged and retried on the first request. */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        for (HierarchyDefinition def : configured.values()) {
            try {
                cache(def.type()).reload();
            } catch (RuntimeException e) {
                log.error("{} hierarchy could not be loaded at startup from KDB table {} (next attempt on first "
                        + "request after {} ms): {}", def.type(), def.table(), retryIntervalMs, e.toString(), e);
            }
        }
    }

    /** Status of every configured hierarchy plus any other type loaded since startup, sorted by type. */
    public List<HierarchyInfo> getHierarchies() {
        Map<String, HierarchyInfo> infos = new TreeMap<>();
        configured.values().forEach(d -> infos.put(d.type(), new HierarchyInfo(d.type(), d.table(), false, 0, null)));
        caches.forEach((type, cache) -> infos.put(type, cache.info()));
        return List.copyOf(infos.values());
    }

    /** Tree of {@code type} from the root, limited to {@code depth} levels (null = full tree). Do not mutate. */
    public HierarchyNode getHierarchy(String type, Integer depth) {
        return cache(type).getHierarchy(depth);
    }

    /** Subtree rooted at {@code nodeId}; empty if the node does not exist in that hierarchy. */
    public Optional<HierarchyNode> getNode(String type, long nodeId, Integer depth) {
        return cache(type).getNode(nodeId, depth);
    }

    /** Direct children of {@code nodeId}; empty if the node does not exist in that hierarchy. */
    public Optional<List<HierarchyNode>> getChildren(String type, long nodeId) {
        return cache(type).getChildren(nodeId);
    }

    /** Re-reads the hierarchy's KDB table into its cache and returns the new status. */
    public HierarchyInfo reload(String type) {
        HierarchyCache cache = cache(type);
        cache.reload();
        return cache.info();
    }

    /**
     * The cache of a type, created on first use. Unconfigured types are only created once their table is known to
     * exist, so requests for made-up types never fill the map.
     */
    HierarchyCache cache(String rawType) {
        String type = HierarchyDefinition.normalizeType(rawType);
        HierarchyCache cache = caches.get(type);
        if (cache != null) {
            return cache;
        }
        HierarchyDefinition def = configured.get(type);
        if (def == null) {
            if (!allowUnconfiguredTypes) {
                throw notFound(type, "Hierarchy type " + type + " is not configured.");
            }
            def = HierarchyDefinition.of(type, null, null);
            if (!tableExists(def.table())) {
                throw notFound(type, "No " + type + " hierarchy exists (KDB table " + def.table() + " not found).");
            }
            log.info("Using unconfigured hierarchy type {} from KDB table {}", type, def.table());
        }
        HierarchyDefinition resolved = def;
        return caches.computeIfAbsent(type, t -> new HierarchyCache(kdbTemplate, resolved, retryIntervalMs));
    }

    /** Convention tables are plain names (validated in HierarchyDefinition), so a root-namespace check suffices. */
    private boolean tableExists(String table) {
        Object result = kdbTemplate.execute("`" + table + " in tables[]");
        return Boolean.TRUE.equals(result);
    }

    private static CcarException notFound(String type, String detail) {
        return new CcarException(ErrorCode.HIERARCHY_NOT_FOUND, detail).with("hierarchyType", type);
    }
}
