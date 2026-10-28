package com.forecast.ccar.controller;

import com.forecast.ccar.service.BuHierarchyService;
import com.forecast.ccar.service.KdbService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/forecast")
public class CcarForecastController {

    @Autowired
    BuHierarchyService hierarchyService;

    private final KdbService kdbService;

    public CcarForecastController(KdbService kdbService) {
        this.kdbService = kdbService;
    }


    @GetMapping(value = "/forecast-ccar", produces = MediaType.APPLICATION_JSON_VALUE)
    public String getForecastCcar() {
        return kdbService.getForecastCcarJson();
    }

    @GetMapping(value="/bu-hierarchy",produces = MediaType.APPLICATION_JSON_VALUE)
    public String getJsonBUHierarchy() throws Exception {
        return hierarchyService.buildJson("BU_HIERARCHY1.csv");
    }
}
