package com.ccar.forecasting;

import com.ccar.forecasting.config.ForecastingProperties;
import com.kx.c;

/** Shared fixtures: a small BU hierarchy as KDB would return it, and properties with 2 periods (T0, T+-1, T+-2). */
public final class TestData {

    public static final long NJ = Long.MIN_VALUE; // q long null 0Nj

    private TestData() {
    }

    public static c.Flip flip(String[] cols, Object... data) {
        return new c.Flip(new c.Dict(cols, data));
    }

    /**
     * <pre>
     * 1 ALL_BANK
     * +-- 2 North America
     * |   +-- 3 United States
     * |   +-- 4 Canada
     * +-- 109 Europe
     *     +-- 110 United Kingdom
     * </pre>
     */
    public static c.Flip buHierarchy() {
        return flip(new String[]{"BU_ID", "BU_NAME", "LEVEL_ID", "BU_ID_LEVEL1", "BU_ID_LEVEL2", "BU_ID_LEVEL3"},
                new long[]{1, 2, 3, 4, 109, 110},
                new String[]{"ALL_BANK", "North America", "United States", "Canada", "Europe", "United Kingdom"},
                new long[]{1, 2, 3, 3, 2, 3},
                new long[]{1, 1, 1, 1, 1, 1},
                new long[]{NJ, 2, 2, 2, 109, 109},
                new long[]{NJ, NJ, 3, 4, NJ, 110});
    }

    public static ForecastingProperties properties() {
        ForecastingProperties p = new ForecastingProperties();
        p.setPeriodCount(2);
        p.setDefaultPageSize(25);
        p.setMaxPageSize(100);
        for (String ds : new String[]{"forecast", "actuals", "variance"}) {
            ForecastingProperties.DataSetTable t = new ForecastingProperties.DataSetTable();
            t.setTable("CCAR_" + ds.toUpperCase());
            p.getDatasets().put(ds, t);
        }
        p.getAccounts().add(new ForecastingProperties.Account("210001", "Balance"));
        p.getAccounts().add(new ForecastingProperties.Account("400001", "Revenue"));
        p.getAccounts().add(new ForecastingProperties.Account("500001", "Expense"));
        ForecastingProperties.HierarchyType bu = new ForecastingProperties.HierarchyType();
        bu.setTable("BU_HIERARCHY");
        p.getHierarchy().getTypes().put("BU", bu);
        return p;
    }
}
