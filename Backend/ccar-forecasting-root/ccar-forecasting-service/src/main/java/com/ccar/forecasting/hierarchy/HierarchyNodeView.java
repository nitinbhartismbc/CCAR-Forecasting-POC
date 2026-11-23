package com.ccar.forecasting.hierarchy;

import com.ccar.forecasting.model.HierarchyNode;

import java.util.List;

/**
 * One node fetched from ccar-hierarchy-service, with its direct children when they were requested.
 *
 * @param definition how the hierarchy type maps onto the KDB fact tables (used for the fact aggregation)
 * @param node       the node itself
 * @param children   direct children in hierarchy order; empty for a leaf or when children were not requested
 */
public record HierarchyNodeView(HierarchyDefinition definition, HierarchyNode node, List<HierarchyNode> children) {

    public HierarchyNodeView {
        children = List.copyOf(children);
    }

    public String type() {
        return definition.type();
    }
}
