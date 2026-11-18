package com.ccar.forecasting.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.LocalDate;
import java.util.List;

/**
 * {@code GET /v1/{forecast|actuals|variance}/detailed-breakdown}: one page (0-based) of the direct children of
 * the selected node, each with its totals.
 */
@JsonPropertyOrder({"scope", "t0Date", "portfolioId", "currency", "periods",
        "page", "size", "totalElements", "totalPages", "items"})
public record DetailedBreakdown(
        Scope scope,
        LocalDate t0Date,
        String portfolioId,
        String currency,
        List<String> periods,
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<BreakdownItem> items) {
}
