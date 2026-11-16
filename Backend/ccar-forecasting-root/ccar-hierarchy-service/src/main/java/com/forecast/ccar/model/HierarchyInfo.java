package com.forecast.ccar.model;

import java.time.Instant;

/**
 * Status of one hierarchy type: returned by {@code GET /forecast/hierarchy} (one per type) and by
 * {@code POST /forecast/hierarchy/{type}/reload}.
 *
 * @param type     hierarchy type, e.g. {@code GEO}
 * @param table    KDB table it is loaded from
 * @param loaded   whether a tree is currently cached
 * @param nodes    number of cached nodes (0 when not loaded)
 * @param loadedAt when the cached tree was loaded ({@code null} when not loaded)
 */
public record HierarchyInfo(String type, String table, boolean loaded, int nodes, Instant loadedAt) {
}
