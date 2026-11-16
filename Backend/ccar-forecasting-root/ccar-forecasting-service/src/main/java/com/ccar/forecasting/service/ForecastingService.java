package com.ccar.forecasting.service;

import com.ccar.forecasting.dto.ForecastRequest;
import com.ccar.forecasting.model.Forecast;
import com.ccar.forecasting.model.Scenario;

import java.util.List;

public interface ForecastingService {

    /** All forecasts, optionally only those of one scenario ({@code null} = all). */
    List<Forecast> getForecasts(Scenario scenario);

    /** @throws com.ccar.common.error.CcarException 404 RESOURCE_NOT_FOUND if there is no such forecast */
    Forecast getForecast(long id);

    Forecast createForecast(ForecastRequest request);

    /** @throws com.ccar.common.error.CcarException 404 RESOURCE_NOT_FOUND if there is no such forecast */
    void deleteForecast(long id);
}
