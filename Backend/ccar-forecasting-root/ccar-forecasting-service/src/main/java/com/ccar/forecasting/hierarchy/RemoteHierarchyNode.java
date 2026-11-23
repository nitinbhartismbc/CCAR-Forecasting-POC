package com.ccar.forecasting.hierarchy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * A node as returned by ccar-hierarchy-service ({@code GET /forecast/hierarchy/{type}/{nodeId}?depth=n}).
 * {@code childCount} is the real number of children even when {@code children} was cut off by {@code depth}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RemoteHierarchyNode(Long id, String name, Integer levelId, Long parentId, int childCount,
                                  List<RemoteHierarchyNode> children) {

    public RemoteHierarchyNode {
        children = children == null ? List.of() : List.copyOf(children);
    }
}
