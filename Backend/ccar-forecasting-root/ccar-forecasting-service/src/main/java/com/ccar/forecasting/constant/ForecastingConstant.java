package com.ccar.forecasting.constant;

/**
 * Fixed technical conventions for the forecasting service that are not meant to vary per deployment/environment
 * (unlike KDB table names, which stay configurable under {@code ccar.forecasting.*} - see
 * {@link com.ccar.forecasting.config.ForecastingProperties}).
 */
public final class ForecastingConstant {

    /** Reporting currency returned with every data response (values are expected in this currency). */
    public static final String DEFAULT_CURRENCY = "USD";

    /** Portfolio id meaning "all portfolios" (no portfolio filter). */
    public static final String ALL_PORTFOLIOS_ID = "PORT_ALL";

    /** Number of periods after T0: T+1..T+n (forecast) / T-1..T-n (actuals, variance). */
    public static final int PERIOD_COUNT = 36;

    /** Default page size for the detailed-breakdown endpoint when {@code size} is not given. */
    public static final int DEFAULT_PAGE_SIZE = 25;

    /** Largest page size the detailed-breakdown endpoint accepts. */
    public static final int MAX_PAGE_SIZE = 500;

    /** Fact table column holding the account id. */
    public static final String FACT_ACCOUNT_COLUMN = "ACCOUNT_ID";

    /** Fact table column holding the portfolio id. */
    public static final String FACT_PORTFOLIO_COLUMN = "PORTFOLIO_ID";

    /** Fact table column holding the T0 date. */
    public static final String FACT_T0_DATE_COLUMN = "T0_DATE";

    /** Fact table column holding the T0 (period 0) value. */
    public static final String FACT_T0_COLUMN = "T0";

    /** Prefix of the fact table's period columns: period n (1..{@link #PERIOD_COUNT}) is column {@code <prefix>n}. */
    public static final String FACT_PERIOD_COLUMN_PREFIX = "T_";

    private ForecastingConstant() {
    }
}
