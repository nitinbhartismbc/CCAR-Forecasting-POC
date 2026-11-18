package com.ccar.forecasting.controller;

import com.ccar.forecasting.model.AggregateSummary;
import com.ccar.forecasting.model.DataSet;
import com.ccar.forecasting.model.DetailedBreakdown;
import com.ccar.forecasting.service.ForecastingService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Forecast, actuals and variance data of one hierarchy node (with context path: {@code /ccar/v1/...}):
 * <ul>
 *   <li>{@code GET /v1/{forecast|actuals|variance}/aggregate-summary} - totals of the node</li>
 *   <li>{@code GET /v1/{forecast|actuals|variance}/detailed-breakdown} - a page of its direct children</li>
 * </ul>
 * Query parameters: {@code hierarchyType} (BU | GEO), {@code hierarchyId}, {@code portfolioId} (default ALL),
 * {@code t0Date} (yyyy-MM-dd); the breakdown also takes {@code page} (0-based) and {@code size}.
 */
@RestController
@RequestMapping(value = "/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class ForecastingController {

    private static final String DATA_SET = "/{dataSet:forecast|actuals|variance}";

    private final ForecastingService service;

    public ForecastingController(ForecastingService service) {
        this.service = service;
    }

    @GetMapping(DATA_SET + "/aggregate-summary")
    public AggregateSummary aggregateSummary(
            @PathVariable String dataSet,
            @RequestParam String hierarchyType,
            @RequestParam long hierarchyId,
            @RequestParam(required = false) String portfolioId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate t0Date) {
        return service.aggregateSummary(DataSet.fromPath(dataSet), hierarchyType, hierarchyId, portfolioId, t0Date);
    }

    @GetMapping(DATA_SET + "/detailed-breakdown")
    public DetailedBreakdown detailedBreakdown(
            @PathVariable String dataSet,
            @RequestParam String hierarchyType,
            @RequestParam long hierarchyId,
            @RequestParam(required = false) String portfolioId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate t0Date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size) {
        return service.detailedBreakdown(DataSet.fromPath(dataSet), hierarchyType, hierarchyId, portfolioId, t0Date,
                page, size);
    }
}
