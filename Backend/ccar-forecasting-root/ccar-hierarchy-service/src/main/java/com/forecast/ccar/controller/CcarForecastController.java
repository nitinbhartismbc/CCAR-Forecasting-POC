package com.forecast.ccar.controller;

import com.forecast.ccar.service.KdbService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Forecast data. The hierarchy endpoints (BU, GEO, ...) are in {@link HierarchyController}. */
@RestController
@RequestMapping("/forecast")
public class CcarForecastController {

    private final KdbService kdbService;

    public CcarForecastController(KdbService kdbService) {
        this.kdbService = kdbService;
    }

    @GetMapping(value = "/forecast-ccar", produces = MediaType.APPLICATION_JSON_VALUE)
    public String getForecastCcar() {
        return kdbService.getForecastCcarJson();
    }
}
