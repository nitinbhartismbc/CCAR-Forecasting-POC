package com.ccar.forecasting.controller;

import com.ccar.common.error.CcarException;
import com.ccar.common.error.ErrorCode;
import com.ccar.forecasting.dto.ForecastRequest;
import com.ccar.forecasting.model.Forecast;
import com.ccar.forecasting.model.Scenario;
import com.ccar.forecasting.service.ForecastingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Controller slice; errors go through ccar-common's auto-configured GlobalExceptionHandler. */
@WebMvcTest(ForecastingController.class)
class ForecastingControllerTest {

    private static final Forecast FORECAST = new Forecast(1L, Scenario.ADVERSE, "BU-100", "NET_INTEREST_INCOME",
            "2027-Q1", new BigDecimal("1250.50"), Instant.parse("2026-11-16T10:00:00Z"));

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ForecastingService forecastingService;

    @Test
    void listByScenario() throws Exception {
        when(forecastingService.getForecasts(Scenario.ADVERSE)).thenReturn(List.of(FORECAST));

        mvc.perform(get("/forecasts").param("scenario", "ADVERSE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].scenario").value("ADVERSE"))
                .andExpect(jsonPath("$[0].amount").value(1250.50));
    }

    @Test
    void create_is201WithLocation() throws Exception {
        when(forecastingService.createForecast(any(ForecastRequest.class))).thenReturn(FORECAST);

        mvc.perform(post("/forecasts").contentType(MediaType.APPLICATION_JSON).content("""
                        {"scenario":"ADVERSE","businessUnit":"BU-100","metric":"NET_INTEREST_INCOME",
                         "period":"2027-Q1","amount":1250.50}"""))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/forecasts/1"))
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void invalidBody_is400ProblemWithEveryField() throws Exception {
        mvc.perform(post("/forecasts").contentType(MediaType.APPLICATION_JSON).content("""
                        {"scenario":"ADVERSE","businessUnit":"","metric":"NII","period":"2027","amount":1}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void unknownId_is404Problem() throws Exception {
        when(forecastingService.getForecast(42L)).thenThrow(
                new CcarException(ErrorCode.RESOURCE_NOT_FOUND, "Forecast not found with id: 42").with("forecastId", 42));

        mvc.perform(get("/forecasts/42").header("X-Correlation-Id", "fc-test-1"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.forecastId").value(42))
                .andExpect(jsonPath("$.correlationId").value("fc-test-1"))
                .andExpect(jsonPath("$.service").value("ccar-forecasting-service"));
    }
}
