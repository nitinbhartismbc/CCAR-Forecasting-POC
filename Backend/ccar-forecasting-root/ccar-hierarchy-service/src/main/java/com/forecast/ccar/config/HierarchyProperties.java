package com.forecast.ccar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code ccar.hierarchy.*}:
 * <pre>
 * ccar.hierarchy.types.BU.table=BU_HIERARCHY
 * ccar.hierarchy.types.GEO.table=GEO_HIERARCHY
 * ccar.hierarchy.types.GEO.column-prefix=GEO           # optional, default = the type
 * ccar.hierarchy.allow-unconfigured-types=true         # XYZ -> table XYZ_HIERARCHY, if it exists in KDB
 * ccar.hierarchy.retry-interval-ms=30000
 * </pre>
 * Configured types are loaded at startup; any other type is loaded on its first request.
 */
@ConfigurationProperties(prefix = "ccar.hierarchy")
public class HierarchyProperties {

    /** Fail-fast window (503 + Retry-After) after a failed load, before KDB is tried again. */
    private long retryIntervalMs = 30_000;

    /** Accept types that are not configured below, using the naming convention (table {@code <TYPE>_HIERARCHY}). */
    private boolean allowUnconfiguredTypes = true;

    /** Known hierarchy types (key = type, case-insensitive). */
    private Map<String, Type> types = new LinkedHashMap<>();

    public static class Type {

        /** KDB table; default {@code <TYPE>_HIERARCHY}. */
        private String table;

        /** Column prefix ({@code <PREFIX>_ID}, {@code <PREFIX>_NAME}, {@code <PREFIX>_ID_LEVELn}); default the type. */
        private String columnPrefix;

        public String getTable() {
            return table;
        }

        public void setTable(String table) {
            this.table = table;
        }

        public String getColumnPrefix() {
            return columnPrefix;
        }

        public void setColumnPrefix(String columnPrefix) {
            this.columnPrefix = columnPrefix;
        }
    }

    public long getRetryIntervalMs() {
        return retryIntervalMs;
    }

    public void setRetryIntervalMs(long retryIntervalMs) {
        this.retryIntervalMs = retryIntervalMs;
    }

    public boolean isAllowUnconfiguredTypes() {
        return allowUnconfiguredTypes;
    }

    public void setAllowUnconfiguredTypes(boolean allowUnconfiguredTypes) {
        this.allowUnconfiguredTypes = allowUnconfiguredTypes;
    }

    public Map<String, Type> getTypes() {
        return types;
    }

    public void setTypes(Map<String, Type> types) {
        this.types = types;
    }
}
