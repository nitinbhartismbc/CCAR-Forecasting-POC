package com.ccar.forecasting.controller;

import com.ccar.forecasting.model.Portfolio;
import com.ccar.forecasting.service.ReferenceDataService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Reference data endpoints used to populate the UI's filters (with context path: {@code /ccar/v1/...}).
 * <p>
 * Backed by {@link ReferenceDataService}, which caches portfolios and T0 dates from KDB+ (fetched at startup).
 */
@RestController
@RequestMapping(value = "/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class ReferenceDataController {

    private final ReferenceDataService service;

    /**
     * @param service provides the (cached) portfolio and T0 date reference data
     */
    public ReferenceDataController(ReferenceDataService service) {
        this.service = service;
    }

    /**
     * All portfolios available for filtering, with the synthetic "ALL" portfolio first.
     *
     * @return {@code [{id, name}, ...]}, "ALL" first
     */
    @GetMapping("/portfolios")
    public List<Portfolio> portfolios() {
        return service.portfolios();
    }

    /**
     * Every T0 date that has data, most recent first.
     *
     * @return {@code ["2025-12-31", ...]}, latest first
     */
    @GetMapping("/t0-dates")
    public List<String> t0Dates() {
        return service.t0Dates();
    }
}
