package com.ccar.forecasting.service;

import com.ccar.common.error.CcarException;
import com.ccar.common.error.ErrorCode;
import com.ccar.forecasting.dto.ForecastRequest;
import com.ccar.forecasting.model.Forecast;
import com.ccar.forecasting.model.Scenario;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ForecastingServiceImplTest {

    private final ForecastingServiceImpl service = new ForecastingServiceImpl();

    @Test
    void createThenGet() {
        Forecast created = service.createForecast(request(Scenario.BASELINE));

        assertThat(created.id()).isEqualTo(1L);
        assertThat(created.createdAt()).isNotNull();
        assertThat(service.getForecast(1L)).isEqualTo(created);
    }

    @Test
    void filtersByScenario() {
        service.createForecast(request(Scenario.BASELINE));
        service.createForecast(request(Scenario.ADVERSE));
        service.createForecast(request(Scenario.ADVERSE));

        assertThat(service.getForecasts(null)).hasSize(3);
        assertThat(service.getForecasts(Scenario.ADVERSE)).extracting(Forecast::id).containsExactly(2L, 3L);
    }

    @Test
    void unknownIdIsResourceNotFound() {
        assertThatThrownBy(() -> service.getForecast(99L))
                .isInstanceOf(CcarException.class)
                .extracting(e -> ((CcarException) e).getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThatThrownBy(() -> service.deleteForecast(99L)).isInstanceOf(CcarException.class);
    }

    @Test
    void delete() {
        service.createForecast(request(Scenario.BASELINE));
        service.deleteForecast(1L);

        assertThat(service.getForecasts(null)).isEmpty();
    }

    private static ForecastRequest request(Scenario scenario) {
        return new ForecastRequest(scenario, "BU-100", "NET_INTEREST_INCOME", "2027-Q1", new BigDecimal("1250.50"));
    }
}
