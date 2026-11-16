package com.forecast.ccar.controller;

import com.ccar.common.error.CcarException;
import com.ccar.common.error.ErrorCode;
import com.forecast.ccar.model.HierarchyInfo;
import com.forecast.ccar.model.HierarchyNode;
import com.forecast.ccar.service.HierarchyService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/**
 * Generic hierarchy API. {@code {type}} selects the hierarchy - {@code BU}, {@code GEO} or any other type whose
 * KDB table exists (case-insensitive). Under {@code /forecast} so it shares the gateway route of this service.
 */
@RestController
@RequestMapping(value = "/forecast/hierarchy", produces = MediaType.APPLICATION_JSON_VALUE)
public class HierarchyController {

    private final HierarchyService hierarchyService;

    public HierarchyController(HierarchyService hierarchyService) {
        this.hierarchyService = hierarchyService;
    }

    /** Available hierarchy types with their table and load status. */
    @GetMapping
    public List<HierarchyInfo> getHierarchies() {
        return hierarchyService.getHierarchies();
    }

    /** Whole tree of one hierarchy, or the top {@code depth} levels below the root when depth is given. */
    @GetMapping("/{type}")
    public HierarchyNode getHierarchy(@PathVariable String type, @RequestParam(required = false) Integer depth) {
        return hierarchyService.getHierarchy(type, checkDepth(depth));
    }

    /** Subtree rooted at a node (default: the node plus its direct children). */
    @GetMapping("/{type}/{nodeId}")
    public HierarchyNode getNode(@PathVariable String type, @PathVariable long nodeId,
                                 @RequestParam(defaultValue = "1") Integer depth) {
        return hierarchyService.getNode(type, nodeId, checkDepth(depth)).orElseThrow(() -> nodeNotFound(type, nodeId));
    }

    /** Direct children of a node, for lazy-loading a UI tree on expand. */
    @GetMapping("/{type}/{nodeId}/children")
    public List<HierarchyNode> getChildren(@PathVariable String type, @PathVariable long nodeId) {
        return hierarchyService.getChildren(type, nodeId).orElseThrow(() -> nodeNotFound(type, nodeId));
    }

    /** Re-reads the hierarchy's KDB table into the cache. */
    @PostMapping("/{type}/reload")
    public HierarchyInfo reload(@PathVariable String type) {
        return hierarchyService.reload(type);
    }

    private static Integer checkDepth(Integer depth) {
        if (depth != null && depth < 0) {
            throw new CcarException(ErrorCode.INVALID_PARAMETER, "depth must be >= 0 but was " + depth)
                    .with("parameter", "depth");
        }
        return depth;
    }

    private static CcarException nodeNotFound(String type, long nodeId) {
        String t = type.toUpperCase(Locale.ROOT);
        return new CcarException(ErrorCode.HIERARCHY_NODE_NOT_FOUND, t + " node " + nodeId + " not found")
                .with("hierarchyType", t)
                .with("nodeId", nodeId);
    }
}
