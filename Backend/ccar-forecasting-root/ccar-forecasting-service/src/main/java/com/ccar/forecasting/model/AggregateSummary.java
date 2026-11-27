package com.ccar.forecasting.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.LocalDate;
import java.util.List;

/**
 * {@code GET /v1/{forecast|actuals|variance}/aggregate-summary}: totals of the selected node.
 * <ul>
 *   <li>actuals: {@code periodValuesActual} only;</li>
 *   <li>forecast: {@code periodValuesForecast} only;</li>
 *   <li>variance: both, so the UI can compare actuals with forecast period by period.</li>
 * </ul>
 * A list that does not belong to the data set is {@code null} and left out of the JSON.
 */
@JsonPropertyOrder({"scope", "t0Date", "portfolioId", "currency", "periods", "periodValuesActual",
        "periodValuesForecast"})
public record AggregateSummary(
        Scope scope,
        LocalDate t0Date,
        String portfolioId,
        String currency,
        List<String> periods,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<PeriodValues> periodValuesActual,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<PeriodValues> periodValuesForecast) {
}
