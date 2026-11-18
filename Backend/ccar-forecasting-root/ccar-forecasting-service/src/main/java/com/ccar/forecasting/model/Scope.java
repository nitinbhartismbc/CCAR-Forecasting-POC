package com.ccar.forecasting.model;

/** The hierarchy node a data response is aggregated for. */
public record Scope(String type, long id, String name, int level) {
}
