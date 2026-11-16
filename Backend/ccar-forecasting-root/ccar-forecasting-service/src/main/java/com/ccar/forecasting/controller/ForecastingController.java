package com.ccar.forecasting.controller;

import com.ccar.forecasting.dto.ForecastRequest;
import com.ccar.forecasting.model.Forecast;
import com.ccar.forecasting.model.Scenario;
import com.ccar.forecasting.service.ForecastingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/forecasts")
public class ForecastingController {

    private final ForecastingService forecastingService;

    public ForecastingController(ForecastingService forecastingService) {
        this.forecastingService = forecastingService;
    }

    /** {@code GET /forecasts} or {@code GET /forecasts?scenario=ADVERSE}. */
    @GetMapping
    public List<Forecast> getForecasts(@RequestParam(required = false) Scenario scenario) {
        return forecastingService.getForecasts(scenario);
    }

    @GetMapping("/{id}")
    public Forecast getForecast(@PathVariable long id) {
        return forecastingService.getForecast(id);
    }

    /** 201 Created with a {@code Location} header pointing at the new forecast. */
    @PostMapping
    public ResponseEntity<Forecast> createForecast(@Valid @RequestBody ForecastRequest request) {
        Forecast created = forecastingService.createForecast(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteForecast(@PathVariable long id) {
        forecastingService.deleteForecast(id);
        return ResponseEntity.noContent().build();
    }
}
