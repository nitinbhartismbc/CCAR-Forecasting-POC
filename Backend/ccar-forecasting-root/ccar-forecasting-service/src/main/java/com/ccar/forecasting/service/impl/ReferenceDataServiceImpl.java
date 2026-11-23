package com.ccar.forecasting.service.impl;

import com.ccar.forecasting.constant.ForecastingConstant;
import com.ccar.forecasting.model.Portfolio;
import com.ccar.forecasting.repository.ReferenceDataRepository;
import com.ccar.forecasting.service.ReferenceDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Default {@link ReferenceDataService} implementation: portfolios and T0 dates are fetched from KDB+ once at
 * startup ({@link #warmUp()}) and kept in memory. A request is served from the cache when it is present; if the
 * startup fetch failed (or hasn't completed yet), it falls back to fetching on demand and populating the cache,
 * so the endpoint still works - just without the startup head start.
 */
@Service
public class ReferenceDataServiceImpl implements ReferenceDataService {

    private static final Logger log = LoggerFactory.getLogger(ReferenceDataServiceImpl.class);

    private final ReferenceDataRepository referenceData;

    private final AtomicReference<List<Portfolio>> portfolioCache = new AtomicReference<>();
    private final AtomicReference<List<String>> t0DateCache = new AtomicReference<>();

    public ReferenceDataServiceImpl(ReferenceDataRepository referenceData) {
        this.referenceData = referenceData;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        try {
            cachePortfoliosIfPresent(loadPortfolios());
            log.info("Portfolio cache warmed up: {} portfolio(s)", sizeOf(portfolioCache.get()));
        } catch (RuntimeException e) {
            log.error("Portfolios could not be loaded at startup (retried on first request): {}", e.toString());
        }
        try {
            cacheT0DatesIfPresent(loadT0Dates());
            log.info("T0 date cache warmed up: {} date(s)", sizeOf(t0DateCache.get()));
        } catch (RuntimeException e) {
            log.error("T0 dates could not be loaded at startup (retried on first request): {}", e.toString());
        }
    }

    @Override
    public List<Portfolio> portfolios() {
        List<Portfolio> cached = portfolioCache.get();
        if (cached != null) {
            return cached;
        }
        List<Portfolio> loaded = loadPortfolios();
        cachePortfoliosIfPresent(loaded);
        return loaded;
    }

    @Override
    public List<String> t0Dates() {
        List<String> cached = t0DateCache.get();
        if (cached != null) {
            return cached;
        }
        List<String> loaded = loadT0Dates();
        cacheT0DatesIfPresent(loaded);
        return loaded;
    }

    /** Re-reads both caches from KDB+. */
    public void refresh() {
        cachePortfoliosIfPresent(loadPortfolios());
        cacheT0DatesIfPresent(loadT0Dates());
    }

    /**
     * Only caches a non-empty result. KDB can legitimately have no portfolios/T0 dates yet (e.g. warm-up runs
     * before the fact tables are populated); caching an empty result as "successful" would otherwise serve that
     * empty list forever, even once real data shows up in KDB - every call would keep retrying until data appears.
     */
    private void cachePortfoliosIfPresent(List<Portfolio> loaded) {
        if (!loaded.isEmpty()) {
            portfolioCache.set(loaded);
        }
    }

    private void cacheT0DatesIfPresent(List<String> loaded) {
        if (!loaded.isEmpty()) {
            t0DateCache.set(loaded);
        }
    }

    private static int sizeOf(List<?> list) {
        return list == null ? 0 : list.size();
    }

    /** "All portfolios" first, then every portfolio from the portfolio master table. */
    private List<Portfolio> loadPortfolios() {
        String all = ForecastingConstant.ALL_PORTFOLIOS_ID;
        List<Portfolio> portfolioList = new ArrayList<>();
        portfolioList.add(new Portfolio(all, "All Portfolios"));
        referenceData.portfolios().stream()
                .filter(p -> !p.id().equalsIgnoreCase(all))
                .forEach(portfolioList::add);
        return List.copyOf(portfolioList);
    }

    private List<String> loadT0Dates() {
        return referenceData.t0Dates().stream().map(LocalDate::toString).toList();
    }
}
