package com.ccar.forecasting.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * One node of a hierarchy (BU, GEO, ...) in the flat list returned by {@code GET /v1/hierarchy/root/{type}}.
 * {@code parentId} is null for the root.
 */
@JsonPropertyOrder({"id", "name", "level", "parentId", "childCount", "isLeafNode"})
public record HierarchyNode(long id, String name, int level, Long parentId, int childCount) {

    @JsonProperty("isLeafNode")
    public boolean isLeafNode() {
        return childCount == 0;
    }
}
