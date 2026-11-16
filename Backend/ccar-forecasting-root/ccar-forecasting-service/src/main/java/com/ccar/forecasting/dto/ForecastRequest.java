package com.ccar.forecasting.dto;

import com.ccar.forecasting.model.Scenario;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/** Body of {@code POST /forecasts}. Validation failures become 400 VALIDATION_FAILED (ccar-common handler). */
public record ForecastRequest(
        @NotNull Scenario scenario,
        @NotBlank String businessUnit,
        @NotBlank String metric,
        @NotBlank @Pattern(regexp = "\\d{4}-Q[1-4]", message = "must look like 2027-Q1") String period,
        @NotNull BigDecimal amount) {
}
