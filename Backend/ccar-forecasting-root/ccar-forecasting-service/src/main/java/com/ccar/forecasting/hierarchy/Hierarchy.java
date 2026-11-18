package com.ccar.forecasting.hierarchy;

import com.ccar.forecasting.model.HierarchyNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable snapshot of one loaded hierarchy: the flat node list (table order) plus id and parent indexes.
 * Safe to share between threads; a reload builds a new snapshot.
 */
public final class Hierarchy {

    private final HierarchyDefinition definition;
    private final List<HierarchyNode> nodes;
    private final Map<Long, HierarchyNode> byId;
    private final Map<Long, List<HierarchyNode>> childrenById;
    private final HierarchyNode root;
    private final Instant loadedAt;

    private Hierarchy(HierarchyDefinition definition, List<HierarchyNode> nodes, Map<Long, HierarchyNode> byId,
                      Map<Long, List<HierarchyNode>> childrenById, HierarchyNode root) {
        this.definition = definition;
        this.nodes = nodes;
        this.byId = byId;
        this.childrenById = childrenById;
        this.root = root;
        this.loadedAt = Instant.now();
    }

    /** One table row before child counts are known. */
    public record RawNode(long id, String name, int level, Long parentId) {
    }

    /**
     * Builds the snapshot from table rows (table order = sibling order). Duplicate ids keep the last row; rows whose
     * parent does not exist stay in the flat list but are nobody's child.
     *
     * @throws IllegalStateException if there is no root (no row without a parent)
     */
    public static Hierarchy build(HierarchyDefinition definition, List<RawNode> rows) {
        Map<Long, RawNode> raw = new LinkedHashMap<>();
        for (RawNode r : rows) {
            raw.put(r.id(), r);
        }
        Map<Long, Integer> childCounts = new HashMap<>();
        for (RawNode r : raw.values()) {
            if (r.parentId() != null && raw.containsKey(r.parentId())) {
                childCounts.merge(r.parentId(), 1, Integer::sum);
            }
        }

        List<HierarchyNode> nodes = new ArrayList<>(raw.size());
        Map<Long, HierarchyNode> byId = new HashMap<>(raw.size() * 2);
        Map<Long, List<HierarchyNode>> children = new HashMap<>();
        HierarchyNode root = null;
        for (RawNode r : raw.values()) {
            HierarchyNode node = new HierarchyNode(r.id(), r.name(), r.level(), r.parentId(),
                    childCounts.getOrDefault(r.id(), 0));
            nodes.add(node);
            byId.put(node.id(), node);
            if (r.parentId() == null) {
                if (root == null) {
                    root = node;
                }
            } else if (raw.containsKey(r.parentId())) {
                children.computeIfAbsent(r.parentId(), k -> new ArrayList<>()).add(node);
            }
        }
        if (root == null) {
            throw new IllegalStateException(definition.type() + " hierarchy has no root (no row with "
                    + definition.levelColumn() + " = 1)");
        }
        children.replaceAll((k, v) -> List.copyOf(v));
        return new Hierarchy(definition, Collections.unmodifiableList(nodes), Collections.unmodifiableMap(byId),
                Collections.unmodifiableMap(children), root);
    }

    public HierarchyDefinition definition() {
        return definition;
    }

    public String type() {
        return definition.type();
    }

    /** Every node, flat, in table order. */
    public List<HierarchyNode> nodes() {
        return nodes;
    }

    public HierarchyNode root() {
        return root;
    }

    public Optional<HierarchyNode> node(long id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** Direct children in table order (empty for a leaf). */
    public List<HierarchyNode> children(long id) {
        return childrenById.getOrDefault(id, List.of());
    }

    public int size() {
        return nodes.size();
    }

    public Instant loadedAt() {
        return loadedAt;
    }
}
