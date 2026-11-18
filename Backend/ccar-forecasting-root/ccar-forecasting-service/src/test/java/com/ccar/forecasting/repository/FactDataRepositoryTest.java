package com.ccar.forecasting.repository;

import com.ccar.forecasting.TestData;
import com.ccar.forecasting.hierarchy.HierarchyDefinition;
import com.ccar.forecasting.model.DataSet;
import com.iris.kdb.exception.KdbQueryException;
import com.iris.kdb.service.KdbTemplate;
import com.kx.c;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
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

        assertEquals("0!select T0:sum T0, N__T0:sum not null T0, T_1:sum T_1, N__T_1:sum not null T_1, "
                + "T_2:sum T_2, N__T_2:sum not null T_2 by NODE__ID, ACCOUNT_ID "
                + "from ej[`BU_ID;select from CCAR_FORECAST where T0_DATE=2026.09.30, PORTFOLIO_ID=`$\"PORT_0001\";"
                + "select BU_ID:BU_ID, NODE__ID:BU_ID_LEVEL2 from BU_HIERARCHY where BU_ID_LEVEL2 in (),2 109]", q);
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
        c.Flip flip = TestData.flip(
                new String[]{"NODE__ID", "ACCOUNT_ID", "T0", "N__T0", "T_1", "N__T_1", "T_2", "N__T_2"},
                new long[]{2, 2, 109},
                new long[]{210001, 400001, 210001},
                new long[]{100, 5, 7},
                new int[]{2, 1, 1},
                new long[]{0, 6, 8},      // sum is 0 for node 2 / Balance because every value was null
                new int[]{0, 1, 1},
                new long[]{NJ, NJ, 9},    // a null sum is treated as no data as well
                new int[]{0, 0, 1});

        Map<Long, Map<String, Number[]>> totals = repo.read(flip);

        assertArrayEquals(new Number[]{100L, null, null}, totals.get(2L).get("210001"));
        assertArrayEquals(new Number[]{5L, 6L, null}, totals.get(2L).get("400001"));
        assertArrayEquals(new Number[]{7L, 8L, 9L}, totals.get(109L).get("210001"));
    }

    @Test
    void symbolAccountIdsAndFloatValuesAreSupported() {
        c.Flip flip = TestData.flip(
                new String[]{"NODE__ID", "ACCOUNT_ID", "T0", "N__T0", "T_1", "N__T_1", "T_2", "N__T_2"},
                new long[]{1}, new String[]{"210001"},
                new double[]{1.5}, new int[]{1}, new double[]{Double.NaN}, new int[]{0}, new double[]{2.5}, new int[]{1});

        assertArrayEquals(new Number[]{1.5, null, 2.5}, repo.read(flip).get(1L).get("210001"));
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
}
