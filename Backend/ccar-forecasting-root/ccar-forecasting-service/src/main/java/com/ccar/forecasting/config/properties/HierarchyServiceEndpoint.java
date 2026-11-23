package com.ccar.forecasting.config.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Where ccar-hierarchy-service is reached ({@code ccar.forecasting.hierarchy-service.*}). Hierarchy nodes (name,
 * level, children) are read from it instead of from the KDB hierarchy tables.
 */
public class HierarchyServiceEndpoint {

    /** Base URL including the service's context path, e.g. {@code http://localhost:8081/ccar}. */
    @NotBlank
    private String baseUrl = "http://localhost:8081/ccar";

    /** TCP connect timeout; exceeding it is reported as 503 DOWNSTREAM_UNAVAILABLE. */
    @Min(1)
    private long connectTimeoutMs = 2_000;

    /** Response timeout; exceeding it is reported as 504 DOWNSTREAM_TIMEOUT. */
    @Min(1)
    private long readTimeoutMs = 10_000;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public long getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(long connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public long getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(long readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }
}
