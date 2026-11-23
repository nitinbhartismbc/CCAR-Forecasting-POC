package com.ccar.forecasting.service;

import com.ccar.forecasting.model.AggregateSummary;
import com.ccar.forecasting.model.DataSet;
import com.ccar.forecasting.model.DetailedBreakdown;

import java.time.LocalDate;

/**
 * Turns the flat KDB results into the JSON the UI renders: periods, one {@code PeriodValues} per account (always
 * every configured account, in configured order, null where there is no data), the selected node as scope and, for
 * the breakdown, a page of its direct children.
 *
 * @see com.ccar.forecasting.service.impl.ForecastingServiceImpl
 */
public interface ForecastingService {


    /**
     * Totals (one row per account) for a single hierarchy node.
     *
     * @param dataSet       forecast, actuals or variance
     * @param hierarchyType {@code BU} or {@code GEO} (case-insensitive)
     * @param hierarchyId   the node's id in that hierarchy
     * @param portfolioId   a portfolio id, or {@code null}/blank/the configured "all" id for no filter
     * @param t0Date        the T0 date the periods are computed relative to
     * @return the node as scope, periods and one {@code PeriodValues} per configured (plus any extra) account
     */
    AggregateSummary aggregateSummary(DataSet dataSet, String hierarchyType, long hierarchyId,
                                       String portfolioId, LocalDate t0Date);

    /**
     * Totals for a page of one hierarchy node's direct children.
     *
     * @param dataSet       forecast, actuals or variance
     * @param hierarchyType {@code BU} or {@code GEO} (case-insensitive)
     * @param hierarchyId   the parent node's id in that hierarchy
     * @param portfolioId   a portfolio id, or {@code null}/blank/the configured "all" id for no filter
     * @param t0Date        the T0 date the periods are computed relative to
     * @param page          zero-based page index of the parent's direct children
     * @param size          page size, or {@code null} to use the configured default
     * @return the parent node as scope, periods, paging info and one breakdown item per child in the page
     */
    DetailedBreakdown detailedBreakdown(DataSet dataSet, String hierarchyType, long hierarchyId,
                                         String portfolioId, LocalDate t0Date, int page, Integer size);
}
