package com.ccar.forecasting.config.properties;

import jakarta.validation.constraints.NotBlank;

/** One data set's (forecast, actuals, variance) KDB table. */
public class DataSetTable {

    @NotBlank
    private String table;

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }
}
