package com.ccar.forecasting.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.LocalDate;
import java.util.List;

/** {@code GET /v1/{forecast|actuals|variance}/aggregate-summary}: totals of the selected node. */
@JsonPropertyOrder({"scope", "t0Date", "portfolioId", "currency", "periods", "period_values"})
public record AggregateSummary(
        Scope scope,
        LocalDate t0Date,
        String portfolioId,
        String currency,
        List<String> periods,
        @JsonProperty("period_values") List<PeriodValues> periodValues) {
}
