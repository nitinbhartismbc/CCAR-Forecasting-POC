package com.ccar.forecasting.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code ccar.forecasting.*}: where the forecast / actuals / variance data lives in KDB and how it is shaped.
 * <p>
 * Every data set is one flat KDB table with this layout (column names configurable under {@code fact.*}):
 * <pre>
 *   BU_ID (j)  GEO_ID (j)  ACCOUNT_ID (j|s)  PORTFOLIO_ID (s)  T0_DATE (d)  T0  T_1 .. T_36 (j|f)
 * </pre>
 * One row per (leaf node, account, portfolio, T0 date). {@code BU_ID} / {@code GEO_ID} are the ids of the node the
 * row is booked against in each hierarchy (the {@code fact-column} of the hierarchy type). Values are summed in KDB
 * over every node under the requested one, using the {@code <PREFIX>_ID_LEVELn} path columns of the hierarchy table.
 */
@ConfigurationProperties(prefix = "ccar.forecasting")
public class ForecastingProperties {

    /** Reporting currency returned with every data response (values are expected in this currency). */
    private String currency = "USD";

    /** Portfolio id meaning "all portfolios" (no portfolio filter). */
    private String allPortfoliosId = "ALL";

    /** Number of periods after T0: T+1..T+n (forecast) / T-1..T-n (actuals, variance). */
    private int periodCount = 36;

    private int defaultPageSize = 25;
    private int maxPageSize = 500;

    /** Table that portfolios and T0 dates are read from. */
    private String referenceTable = "CCAR_FORECAST";

    /** Data set (forecast, actuals, variance) -> KDB table. */
    private Map<String, DataSetTable> datasets = new LinkedHashMap<>();

    private Fact fact = new Fact();

    /** Accounts (measures) in display order. */
    private List<Account> accounts = new ArrayList<>();

    private Hierarchy hierarchy = new Hierarchy();

    public static class DataSetTable {
        private String table;

        public String getTable() {
            return table;
        }

        public void setTable(String table) {
            this.table = table;
        }
    }

    public static class Fact {
        private String accountColumn = "ACCOUNT_ID";
        private String portfolioColumn = "PORTFOLIO_ID";
        private String t0DateColumn = "T0_DATE";
        private String t0Column = "T0";
        private String periodColumnPrefix = "T_";

        public String getAccountColumn() {
            return accountColumn;
        }

        public void setAccountColumn(String accountColumn) {
            this.accountColumn = accountColumn;
        }

        public String getPortfolioColumn() {
            return portfolioColumn;
        }

        public void setPortfolioColumn(String portfolioColumn) {
            this.portfolioColumn = portfolioColumn;
        }

        public String getT0DateColumn() {
            return t0DateColumn;
        }

        public void setT0DateColumn(String t0DateColumn) {
            this.t0DateColumn = t0DateColumn;
        }

        public String getT0Column() {
            return t0Column;
        }

        public void setT0Column(String t0Column) {
            this.t0Column = t0Column;
        }

        public String getPeriodColumnPrefix() {
            return periodColumnPrefix;
        }

        public void setPeriodColumnPrefix(String periodColumnPrefix) {
            this.periodColumnPrefix = periodColumnPrefix;
        }
    }

    public static class Account {
        private String id;
        private String measure;

        public Account() {
        }

        public Account(String id, String measure) {
            this.id = id;
            this.measure = measure;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getMeasure() {
            return measure;
        }

        public void setMeasure(String measure) {
            this.measure = measure;
        }
    }

    public static class Hierarchy {
        /** Supported hierarchy types (key = type, case-insensitive). */
        private Map<String, HierarchyType> types = new LinkedHashMap<>();

        public Map<String, HierarchyType> getTypes() {
            return types;
        }

        public void setTypes(Map<String, HierarchyType> types) {
            this.types = types;
        }
    }

    public static class HierarchyType {
        /** KDB table; default {@code <TYPE>_HIERARCHY}. */
        private String table;
        /** Column prefix of the hierarchy table; default the type. */
        private String columnPrefix;
        /** Fact-table column holding this hierarchy's node id; default {@code <PREFIX>_ID}. */
        private String factColumn;

        public String getTable() {
            return table;
        }

        public void setTable(String table) {
            this.table = table;
        }

        public String getColumnPrefix() {
            return columnPrefix;
        }

        public void setColumnPrefix(String columnPrefix) {
            this.columnPrefix = columnPrefix;
        }

        public String getFactColumn() {
            return factColumn;
        }

        public void setFactColumn(String factColumn) {
            this.factColumn = factColumn;
        }
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getAllPortfoliosId() {
        return allPortfoliosId;
    }

    public void setAllPortfoliosId(String allPortfoliosId) {
        this.allPortfoliosId = allPortfoliosId;
    }

    public int getPeriodCount() {
        return periodCount;
    }

    public void setPeriodCount(int periodCount) {
        this.periodCount = periodCount;
    }

    public int getDefaultPageSize() {
        return defaultPageSize;
    }

    public void setDefaultPageSize(int defaultPageSize) {
        this.defaultPageSize = defaultPageSize;
    }

    public int getMaxPageSize() {
        return maxPageSize;
    }

    public void setMaxPageSize(int maxPageSize) {
        this.maxPageSize = maxPageSize;
    }

    public String getReferenceTable() {
        return referenceTable;
    }

    public void setReferenceTable(String referenceTable) {
        this.referenceTable = referenceTable;
    }

    public Map<String, DataSetTable> getDatasets() {
        return datasets;
    }

    public void setDatasets(Map<String, DataSetTable> datasets) {
        this.datasets = datasets;
    }

    public Fact getFact() {
        return fact;
    }

    public void setFact(Fact fact) {
        this.fact = fact;
    }

    public List<Account> getAccounts() {
        return accounts;
    }

    public void setAccounts(List<Account> accounts) {
        this.accounts = accounts;
    }

    public Hierarchy getHierarchy() {
        return hierarchy;
    }

    public void setHierarchy(Hierarchy hierarchy) {
        this.hierarchy = hierarchy;
    }
}
