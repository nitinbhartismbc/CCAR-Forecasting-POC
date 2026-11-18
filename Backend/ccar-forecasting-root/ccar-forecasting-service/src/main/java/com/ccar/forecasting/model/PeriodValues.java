package com.ccar.forecasting.model;

import java.util.List;

/**
 * One measure over all periods. {@code values[i]} belongs to {@code periods[i]} of the enclosing response and is
 * {@code null} when there is no data for that period (the UI shows "-").
 */
public record PeriodValues(String accountId, String measure, List<Number> values) {
}
