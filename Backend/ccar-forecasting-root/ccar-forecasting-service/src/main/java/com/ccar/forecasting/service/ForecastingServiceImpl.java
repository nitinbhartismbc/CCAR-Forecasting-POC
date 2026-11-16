package com.ccar.forecasting.service;

import com.ccar.common.error.CcarException;
import com.ccar.common.error.ErrorCode;
import com.ccar.forecasting.dto.ForecastRequest;
import com.ccar.forecasting.model.Forecast;
import com.ccar.forecasting.model.Scenario;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Starter implementation backed by an in-memory map - replace the store with the real one (e.g. KDB+ via
 * kdb-starter, or JPA) without changing the controller. Errors are thrown as {@link CcarException}s and turned
 * into problem+json by ccar-common's GlobalExceptionHandler; entry/exit is logged by ccar-common's LoggingAspect.
 */
@Service
public class ForecastingServiceImpl implements ForecastingService {

    private final Map<Long, Forecast> store = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    @Override
    public List<Forecast> getForecasts(Scenario scenario) {
        return store.values().stream()
                .filter(f -> scenario == null || f.scenario() == scenario)
                .sorted(Comparator.comparing(Forecast::id))
                .toList();
    }

    @Override
    public Forecast getForecast(long id) {
        Forecast forecast = store.get(id);
        if (forecast == null) {
            throw notFound(id);
        }
        return forecast;
    }

    @Override
    public Forecast createForecast(ForecastRequest request) {
        long id = ids.incrementAndGet();
        Forecast forecast = new Forecast(id, request.scenario(), request.businessUnit(), request.metric(),
                request.period(), request.amount(), Instant.now());
        store.put(id, forecast);
        return forecast;
    }

    @Override
    public void deleteForecast(long id) {
        if (store.remove(id) == null) {
            throw notFound(id);
        }
    }

    private static CcarException notFound(long id) {
        return new CcarException(ErrorCode.RESOURCE_NOT_FOUND, "Forecast not found with id: " + id)
                .with("forecastId", id);
    }
}
