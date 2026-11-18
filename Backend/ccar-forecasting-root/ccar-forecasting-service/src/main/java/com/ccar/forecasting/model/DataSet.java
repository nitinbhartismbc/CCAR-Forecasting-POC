package com.ccar.forecasting.model;

import com.ccar.common.error.CcarException;
import com.ccar.common.error.ErrorCode;

import java.util.Locale;

/**
 * The three data sets the UI can show. They share one table layout; forecasts look forward from T0
 * ({@code T0, T+1 .. T+n}), actuals and variance look back ({@code T0, T-1 .. T-n}).
 */
public enum DataSet {
    FORECAST("forecast", '+'),
    ACTUALS("actuals", '-'),
    VARIANCE("variance", '-');

    private final String path;
    private final char direction;

    DataSet(String path, char direction) {
        this.path = path;
        this.direction = direction;
    }

    /** URL segment and config key, e.g. {@code forecast}. */
    public String path() {
        return path;
    }

    /** Label of period {@code n} (0 = T0): {@code T0}, {@code T+3}, {@code T-3}. */
    public String periodLabel(int n) {
        return n == 0 ? "T0" : "T" + direction + n;
    }

    public static DataSet fromPath(String path) {
        String p = path == null ? "" : path.trim().toLowerCase(Locale.ROOT);
        for (DataSet d : values()) {
            if (d.path.equals(p)) {
                return d;
            }
        }
        throw new CcarException(ErrorCode.RESOURCE_NOT_FOUND, "Unknown data set: " + path);
    }
}
