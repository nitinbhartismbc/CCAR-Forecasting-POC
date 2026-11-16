package com.ccar.forecasting.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One forecast value: a metric of a business unit for a quarter under a stress scenario.
 *
 * @param id           server-assigned id
 * @param scenario     stress scenario the value was projected under
 * @param businessUnit business unit code (as in the BU hierarchy)
 * @param metric       forecast line item, e.g. {@code NET_INTEREST_INCOME}
 * @param period       forecast quarter, e.g. {@code 2027-Q1}
 * @param amount       projected amount
 * @param createdAt    when the forecast was recorded
 */
public record Forecast(
        Long id,
        Scenario scenario,
        String businessUnit,
        String metric,
        String period,
        BigDecimal amount,
        Instant createdAt) {
}
