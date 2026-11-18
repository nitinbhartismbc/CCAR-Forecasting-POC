package com.ccar.forecasting.controller;

import com.ccar.forecasting.model.HierarchyNode;
import com.ccar.forecasting.model.Portfolio;
import com.ccar.forecasting.service.ForecastingService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Filters and hierarchy for the UI (with context path: {@code /ccar/v1/...}). */
@RestController
@RequestMapping(value = "/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class ReferenceDataController {

    private final ForecastingService service;

    public ReferenceDataController(ForecastingService service) {
        this.service = service;
    }

    /** {@code [{id, name}]}, "ALL" first. */
    @GetMapping("/portfolios")
    public List<Portfolio> portfolios() {
        return service.portfolios();
    }

    /** {@code ["2025-12-31", ...]}, latest first. */
    @GetMapping("/t0-dates")
    public List<String> t0Dates() {
        return service.t0Dates();
    }

    /** Whole hierarchy, flat: {@code [{id, name, level, parentId, childCount, isLeafNode}]}. */
    @GetMapping("/hierarchy/root/{type}")
    public List<HierarchyNode> hierarchyRoot(@PathVariable String type) {
        return service.hierarchy(type);
    }

    /** Re-reads the hierarchy table from KDB into the cache. */
    @PostMapping("/hierarchy/{type}/reload")
    public Map<String, Object> reloadHierarchy(@PathVariable String type) {
        return Map.of("type", type.toUpperCase(java.util.Locale.ROOT), "nodes", service.reloadHierarchy(type));
    }
}
