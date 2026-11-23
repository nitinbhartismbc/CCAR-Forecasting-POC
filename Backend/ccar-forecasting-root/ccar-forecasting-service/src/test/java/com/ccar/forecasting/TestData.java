package com.ccar.forecasting;

import com.ccar.forecasting.config.ForecastingProperties;
import com.ccar.forecasting.config.properties.Account;
import com.ccar.forecasting.config.properties.DataSetTable;
import com.ccar.forecasting.config.properties.HierarchyType;
import com.ccar.forecasting.constant.ForecastingConstant;
import com.ccar.forecasting.hierarchy.RemoteHierarchyNode;
import com.kx.c;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

/** Shared fixtures: a small BU hierarchy as ccar-hierarchy-service returns it, and properties with the fixed 36-period layout. */
public final class TestData {

    public static final long NJ = Long.MIN_VALUE; // q long null 0Nj

    private TestData() {
    }

    public static c.Flip flip(String[] cols, Object... data) {
        return new c.Flip(new c.Dict(cols, data));
    }

    /**
     * Builds a fact-query result flip containing every column {@code FactDataRepository} expects
     * ({@code NODE__ID, ACCOUNT_ID, T0, N__T0, T_1, N__T_1, ..., T_36, N__T_36}), using the given values for the
     * periods actually supplied and nulls/zero-counts for every other period, so tests only need to spell out the
     * periods they care about.
     *
     * @param nodeIds           {@code long[]} column
     * @param accountIds        {@code long[]} or {@code String[]} column
     * @param suppliedPeriodCols triples of {@code (periodColumnName, sumArray, countArray)}, e.g.
     *                           {@code "T0", new long[]{1,2}, new int[]{1,1}}
     */
    public static c.Flip wideFacts(Object nodeIds, Object accountIds, Object... suppliedPeriodCols) {
        int rows = Array.getLength(nodeIds);
        List<String> colNames = new ArrayList<>();
        List<Object> colData = new ArrayList<>();
        colNames.add("NODE__ID");
        colData.add(nodeIds);
        colNames.add("ACCOUNT_ID");
        colData.add(accountIds);

        List<String> allPeriodCols = new ArrayList<>();
        allPeriodCols.add("T0");
        for (int n = 1; n <= ForecastingConstant.PERIOD_COUNT; n++) {
            allPeriodCols.add("T_" + n);
        }
        for (String col : allPeriodCols) {
            colNames.add(col);
            colNames.add("N__" + col);
            int idx = indexOf(suppliedPeriodCols, col);
            if (idx >= 0) {
                colData.add(suppliedPeriodCols[idx + 1]);
                colData.add(suppliedPeriodCols[idx + 2]);
            } else {
                colData.add(new long[rows]); // ignored: N__ below is 0, so "no data"
                colData.add(new int[rows]);  // N__ = 0 -> treated as "no data"
            }
        }
        return flip(colNames.toArray(new String[0]), colData.toArray());
    }

    private static int indexOf(Object[] triples, String name) {
        for (int i = 0; i < triples.length; i += 3) {
            if (name.equals(triples[i])) {
                return i;
            }
        }
        return -1;
    }

    /** {@code leading} values followed by nulls up to the fixed 37-period width (T0, T_1 .. T_36). */
    public static Number[] periods(Number... leading) {
        Number[] values = new Number[ForecastingConstant.PERIOD_COUNT + 1];
        System.arraycopy(leading, 0, values, 0, leading.length);
        return values;
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
    public static RemoteHierarchyNode buTree() {
        return node(1, "ALL_BANK", 1, null,
                node(2, "North America", 2, 1L,
                        node(3, "United States", 3, 2L),
                        node(4, "Canada", 3, 2L)),
                node(109, "Europe", 2, 1L,
                        node(110, "United Kingdom", 3, 109L)));
    }

    /** {@code root}'s node {@code id} cut to {@code depth} levels of children, as the hierarchy service does. */
    public static RemoteHierarchyNode find(RemoteHierarchyNode root, long id, int depth) {
        if (root.id() == id) {
            return truncate(root, depth);
        }
        for (RemoteHierarchyNode child : root.children()) {
            RemoteHierarchyNode found = find(child, id, depth);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static RemoteHierarchyNode truncate(RemoteHierarchyNode n, int depth) {
        List<RemoteHierarchyNode> children = depth == 0 ? List.of()
                : n.children().stream().map(c -> truncate(c, depth - 1)).toList();
        return new RemoteHierarchyNode(n.id(), n.name(), n.levelId(), n.parentId(), n.childCount(), children);
    }

    private static RemoteHierarchyNode node(long id, String name, int level, Long parentId,
                                            RemoteHierarchyNode... children) {
        return new RemoteHierarchyNode(id, name, level, parentId, children.length, List.of(children));
    }

    public static ForecastingProperties properties() {
        ForecastingProperties p = new ForecastingProperties();
        for (String ds : new String[]{"forecast", "actuals", "variance"}) {
            DataSetTable t = new DataSetTable();
            t.setTable("CCAR_" + ds.toUpperCase());
            p.getDatasets().put(ds, t);
        }
        p.getAccounts().add(new Account("210001", "Balance"));
        p.getAccounts().add(new Account("400001", "Revenue"));
        p.getAccounts().add(new Account("500001", "Expense"));
        HierarchyType bu = new HierarchyType();
        bu.setTable("BU_HIERARCHY");
        p.getHierarchy().getTypes().put("BU", bu);
        return p;
    }
}
