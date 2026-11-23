package com.ccar.forecasting.config.properties;

import jakarta.validation.constraints.NotBlank;

/** Portfolio reference table: {@code PORTFOLIO_MASTER(PORTFOLIO_ID, PORTFOLIO_NAME)} by default. */
public class PortfolioMaster {

    @NotBlank
    private String table = "PORTFOLIO_MASTER";
    @NotBlank
    private String idColumn = "PORTFOLIO_ID";
    @NotBlank
    private String nameColumn = "PORTFOLIO_NAME";

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public String getIdColumn() {
        return idColumn;
    }

    public void setIdColumn(String idColumn) {
        this.idColumn = idColumn;
    }

    public String getNameColumn() {
        return nameColumn;
    }

    public void setNameColumn(String nameColumn) {
        this.nameColumn = nameColumn;
    }
}
