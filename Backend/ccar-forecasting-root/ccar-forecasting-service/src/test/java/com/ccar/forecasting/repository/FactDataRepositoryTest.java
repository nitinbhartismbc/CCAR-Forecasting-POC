package com.ccar.forecasting.repository;

import com.ccar.forecasting.TestData;
import com.ccar.forecasting.hierarchy.HierarchyDefinition;
import com.ccar.forecasting.model.DataSet;
import com.iris.kdb.exception.KdbQueryException;
import com.iris.kdb.service.KdbTemplate;
import com.kx.c;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.ccar.forecasting.TestData.NJ;
import static org.junit.jupiter.api.Assertions.*;

class FactDataRepositoryTest {

    private static final HierarchyDefinition BU = HierarchyDefinition.of("BU", null, null, null);

    private final FactDataRepository repo = new FactDataRepository(new KdbTemplate(null), TestData.properties());

    @Test
    void buildsAggregationQueryGroupedByAncestorAtLevel() {
        String q = repo.buildQuery(DataSet.FORECAST, BU, 2, List.of(2L, 109L), LocalDate.of(2026, 9, 30), "PORT_0001");

        // full query covers all 37 periods (T0, T_1 .. T_36); spot-check its shape instead of the full text
        assertTrue(q.startsWith("0!select T0:sum T0, N__T0:sum not null T0, "
                + "T_1:sum T_1, N__T_1:sum not null T_1, "));
        assertTrue(q.contains("T_36:sum T_36, N__T_36:sum not null T_36"));
        assertTrue(q.contains(" by NODE__ID, ACCOUNT_ID "));
        assertTrue(q.contains("from ej[`BU_ID;select from CCAR_FORECAST where T0_DATE=2026.09.30, "
                + "PORTFOLIO_ID=`$\"PORT_0001\";"));
        assertTrue(q.endsWith("select BU_ID:BU_ID, NODE__ID:BU_ID_LEVEL2 from BU_HIERARCHY "
                + "where BU_ID_LEVEL2 in (),2 109]"));
    }

    @Test
    void allPortfoliosHasNoPortfolioFilterAndUsesTheDataSetTable() {
        String q = repo.buildQuery(DataSet.ACTUALS, BU, 1, List.of(1L), LocalDate.of(2025, 12, 31), null);

        assertTrue(q.contains("select from CCAR_ACTUALS where T0_DATE=2025.12.31;"));
        assertFalse(q.contains("PORTFOLIO_ID"));
        assertTrue(q.contains("where BU_ID_LEVEL1 in (),1]"));
    }

    @Test
    void readsFlipAndTurnsPeriodsWithoutDataIntoNull() {
        c.Flip flip = TestData.wideFacts(
                new long[]{2, 2, 109},
                new long[]{210001, 400001, 210001},
                "T0", new long[]{100, 5, 7}, new int[]{2, 1, 1},
                "T_1", new long[]{0, 6, 8}, new int[]{0, 1, 1},   // sum 0 for node 2/Balance: every value was null
                "T_2", new long[]{NJ, NJ, 9}, new int[]{0, 0, 1}); // a null sum is treated as no data as well

        Map<Long, Map<String, Number[]>> totals = repo.read(flip);

        assertArrayEquals(TestData.periods(100L), totals.get(2L).get("210001"));
        assertArrayEquals(TestData.periods(5L, 6L), totals.get(2L).get("400001"));
        assertArrayEquals(TestData.periods(7L, 8L, 9L), totals.get(109L).get("210001"));
    }

    @Test
    void symbolAccountIdsAndFloatValuesAreSupported() {
        c.Flip flip = TestData.wideFacts(
                new long[]{1}, new String[]{"210001"},
                "T0", new double[]{1.5}, new int[]{1},
                "T_1", new double[]{Double.NaN}, new int[]{0},
                "T_2", new double[]{2.5}, new int[]{1});

        assertArrayEquals(TestData.periods(1.5, null, 2.5), repo.read(flip).get(1L).get("210001"));
    }

    @Test
    void unexpectedResultLayoutIsAKdbQueryFailure() {
        c.Flip wrong = TestData.flip(new String[]{"x"}, new long[]{1});
        KdbTemplate kdb = new KdbTemplate(null) {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T execute(String query, Class<T> clazz) {
                return (T) wrong;
            }
        };
        FactDataRepository r = new FactDataRepository(kdb, TestData.properties());

        assertThrows(KdbQueryException.class,
                () -> r.totals(DataSet.FORECAST, BU, 1, List.of(1L), LocalDate.of(2026, 9, 30), null));
    }

    @Test
    void noNodesNoQuery() {
        assertTrue(repo.totals(DataSet.FORECAST, BU, 1, List.of(), LocalDate.now(), null).isEmpty());
    }

    // ---- nodeTotals: KDB aggregate functions ----------------------------------------------------------------

    /** Records every function call and answers with {@code result}. */
    private static final class FunctionKdb extends KdbTemplate {
        final List<String> functions = new ArrayList<>();
        final List<Object[]> args = new ArrayList<>();
        final c.Flip result;

        FunctionKdb(c.Flip result) {
            super(null);
            this.result = result;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T call(String function, Class<T> clazz, Object... a) {
            functions.add(function);
            args.add(a);
            return (T) result;
        }

        @Override
        public <T> T execute(String query, Class<T> clazz) {
            throw new AssertionError("no query expected: " + query);
        }
    }

    @Test
    void nodeTotalsCallsTheActualsFunctionWithTypedArgs() {
        LocalDate t0 = LocalDate.of(2026, 9, 30);
        FunctionKdb kdb = new FunctionKdb(TestData.accountFacts(new long[]{210001, 400001},
                "T0", new double[]{100.5, 7}, "T_2", new double[]{Double.NaN, 9}));
        FactDataRepository r = new FactDataRepository(kdb, TestData.properties());

        Map<String, Number[]> totals = r.nodeTotals(DataSet.ACTUALS, BU, 2L, t0, "PORT_0001", "USD");

        assertEquals(List.of("getAggregateActuals"), kdb.functions);
        assertArrayEquals(new Object[]{"BU", 2L, "PORT_0001", "USD", t0}, kdb.args.get(0));
        assertArrayEquals(TestData.periods(100.5), totals.get("210001"));
        assertArrayEquals(TestData.periods(7.0, null, 9.0), totals.get("400001"));
    }

    @Test
    void nodeTotalsForAllPortfoliosSendsAllAndUsesTheForecastFunction() {
        FunctionKdb kdb = new FunctionKdb(TestData.accountFacts(new long[0]));
        FactDataRepository r = new FactDataRepository(kdb, TestData.properties());

        assertTrue(r.nodeTotals(DataSet.FORECAST, BU, 1L, LocalDate.of(2026, 9, 30), null, "USD").isEmpty());
        assertEquals(List.of("getAggregateForecast"), kdb.functions);
        assertEquals("ALL", kdb.args.get(0)[2]);
    }

    @Test
    void nodeTotalsWithoutAConfiguredFunctionFails() {
        assertThrows(IllegalStateException.class,
                () -> repo.nodeTotals(DataSet.VARIANCE, BU, 1L, LocalDate.of(2026, 9, 30), null, "USD"));
    }

    @Test
    void nodeTotalsWithAnUnexpectedResultLayoutIsAKdbQueryFailure() {
        FactDataRepository r = new FactDataRepository(
                new FunctionKdb(TestData.flip(new String[]{"x"}, new long[]{1})), TestData.properties());

        assertThrows(KdbQueryException.class,
                () -> r.nodeTotals(DataSet.ACTUALS, BU, 1L, LocalDate.of(2026, 9, 30), null, "USD"));
    }

    @Test
    void unsafeFunctionNameIsRejectedAtStartup() {
        var props = TestData.properties();
        props.getDatasets().get("actuals").setAggregateFunction("getAggregateActuals;delete");

        assertThrows(IllegalArgumentException.class, () -> new FactDataRepository(new KdbTemplate(null), props));
    }
}
