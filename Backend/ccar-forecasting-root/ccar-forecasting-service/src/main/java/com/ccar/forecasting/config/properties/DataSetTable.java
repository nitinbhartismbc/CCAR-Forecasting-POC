package com.ccar.forecasting.config.properties;

import jakarta.validation.constraints.NotBlank;

/**
 * One data set's (forecast, actuals, variance) KDB table and, optionally, the q function on the KDB server that
 * returns the aggregate summary of one hierarchy node from it.
 */
public class DataSetTable {

    @NotBlank
    private String table;

    /**
     * q function {@code fn[hierarchyType; hierarchyId; portfolioId; currencyCode; t0Date]} returning one row per
     * account ({@code ACCOUNT_ID, ACCOUNT_NAME, TO_DATE, T0, T_1 .. T_36}), e.g. {@code getAggregateActuals}.
     * Not set: the data set has no aggregate-summary function.
     */
    private String aggregateFunction;

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public String getAggregateFunction() {
        return aggregateFunction;
    }

    public void setAggregateFunction(String aggregateFunction) {
        this.aggregateFunction = aggregateFunction;
    }
}
