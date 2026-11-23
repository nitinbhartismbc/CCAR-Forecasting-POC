package com.ccar.forecasting.hierarchy;

import com.ccar.common.error.CcarException;
import com.ccar.common.error.ErrorCode;
import com.ccar.forecasting.config.ForecastingProperties;
import com.ccar.forecasting.model.HierarchyNode;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Hierarchy nodes for the forecasting endpoints. The nodes themselves (name, level, children) come from
 * ccar-hierarchy-service, which owns and caches the hierarchy tables; this service no longer reads them from KDB.
 * <p>
 * Only the configured types ({@code ccar.forecasting.hierarchy.types.*}) are accepted, because each one also needs
 * a {@link HierarchyDefinition} telling the KDB fact aggregation which columns to join on.
 */
@Component
public class HierarchyRepository {

    private final HierarchyServiceClient hierarchyService;
    private final Map<String, HierarchyDefinition> definitions;

    public HierarchyRepository(HierarchyServiceClient hierarchyService, ForecastingProperties properties) {
        this.hierarchyService = hierarchyService;
        Map<String, HierarchyDefinition> defs = new LinkedHashMap<>();
        properties.getHierarchy().getTypes().forEach((type, cfg) -> {
            HierarchyDefinition def = HierarchyDefinition.of(type, cfg.getTable(), cfg.getColumnPrefix(),
                    cfg.getFactColumn());
            defs.put(def.type(), def);
        });
        this.definitions = Collections.unmodifiableMap(defs);
    }

    /**
     * The definition of a configured hierarchy type.
     *
     * @throws CcarException 400 INVALID_PARAMETER for a malformed type, 404 HIERARCHY_NOT_FOUND for an unsupported one
     */
    public HierarchyDefinition definition(String type) {
        String t = HierarchyDefinition.normalizeType(type);
        HierarchyDefinition def = definitions.get(t);
        if (def == null) {
            throw new CcarException(ErrorCode.HIERARCHY_NOT_FOUND,
                    "Hierarchy type " + t + " is not supported. Supported types: " + definitions.keySet())
                    .with("hierarchyType", t);
        }
        return def;
    }

    /**
     * Node {@code id} of the hierarchy, fetched from ccar-hierarchy-service.
     *
     * @param withChildren also fetch its direct children (one extra level in the same call)
     * @return empty if the hierarchy has no such node
     * @throws CcarException when the hierarchy service fails, see {@link HierarchyServiceClient}
     */
    public Optional<HierarchyNodeView> find(HierarchyDefinition def, long id, boolean withChildren) {
        return hierarchyService.getNode(def.type(), id, withChildren ? 1 : 0)
                .map(remote -> new HierarchyNodeView(def, toNode(remote),
                        withChildren ? remote.children().stream().map(HierarchyRepository::toNode).toList()
                                : List.of()));
    }

    private static HierarchyNode toNode(RemoteHierarchyNode n) {
        return new HierarchyNode(n.id(), n.name(), n.levelId(), n.parentId(), n.childCount());
    }
}
