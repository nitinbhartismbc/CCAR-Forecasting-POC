package com.ccar.forecasting.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.LinkedHashMap;
import java.util.Map;

/** Supported hierarchy types (key = type, case-insensitive). */
public class Hierarchy {

    /** At least one hierarchy type must be configured. */
    @NotEmpty
    private Map<String, @Valid HierarchyType> types = new LinkedHashMap<>();

    public Map<String, HierarchyType> getTypes() {
        return types;
    }

    public void setTypes(Map<String, HierarchyType> types) {
        this.types = types;
    }
}
