package com.ccar.forecasting.service;

import com.ccar.forecasting.model.Portfolio;

import java.util.List;

/**
 * Reference data for the UI's filters: portfolios and T0 dates. Fetched from KDB+ once at startup and cached in
 * memory (these change rarely compared to how often the UI asks for them).
 *
 * @see com.ccar.forecasting.service.impl.ReferenceDataServiceImpl
 */
public interface ReferenceDataService {

    /**
     * All portfolios available for filtering, served from cache when present.
     *
     * @return "All portfolios" first, then every portfolio from the portfolio master table
     */
    List<Portfolio> portfolios();

    /**
     * Every T0 date that has data, served from cache when present.
     *
     * @return dates in {@code yyyy-MM-dd} form, latest first
     */
    List<String> t0Dates();
}
