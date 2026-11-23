package com.ccar.forecasting.config.properties;

/** One hierarchy type's (BU, GEO, ...) KDB table layout. */
public class HierarchyType {

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
