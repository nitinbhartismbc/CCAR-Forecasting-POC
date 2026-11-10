package com.forecast.ccar.controller;

import com.forecast.ccar.model.BuNode;
import com.forecast.ccar.service.BuHierarchyService;
import com.forecast.ccar.service.KdbService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/forecast")
public class CcarForecastController {

    private final KdbService kdbService;
    private final BuHierarchyService hierarchyService;

    public CcarForecastController(KdbService kdbService, BuHierarchyService hierarchyService) {
        this.kdbService = kdbService;
        this.hierarchyService = hierarchyService;
    }

    @GetMapping(value = "/forecast-ccar", produces = MediaType.APPLICATION_JSON_VALUE)
    public String getForecastCcar() {
        return kdbService.getForecastCcarJson();
    }

    /** Whole BU tree, or the top {@code depth} levels below the root when depth is given. */
    @GetMapping(value = "/bu-hierarchy", produces = MediaType.APPLICATION_JSON_VALUE)
    public BuNode getBuHierarchy(@RequestParam(required = false) Integer depth) {
        return hierarchyService.getHierarchy(depth);
    }

    /** Subtree rooted at a BU (default: the node plus its direct children). */
    @GetMapping(value = "/bu-hierarchy/{buId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public BuNode getBuNode(@PathVariable long buId, @RequestParam(defaultValue = "1") Integer depth) {
        return hierarchyService.getNode(buId, depth).orElseThrow(() -> notFound(buId));
    }

    /** Direct children of a BU, for lazy-loading a UI tree on expand. */
    @GetMapping(value = "/bu-hierarchy/{buId}/children", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<BuNode> getBuChildren(@PathVariable long buId) {
        return hierarchyService.getChildren(buId).orElseThrow(() -> notFound(buId));
    }

    /** Re-reads the CSV into the cache. */
    @PostMapping(value = "/bu-hierarchy/reload", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> reloadBuHierarchy() {
        hierarchyService.reload();
        return Map.of("nodes", hierarchyService.size(), "loadedAt", hierarchyService.loadedAt().toString());
    }

    private static ResponseStatusException notFound(long buId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "BU " + buId + " not found");
    }
}
