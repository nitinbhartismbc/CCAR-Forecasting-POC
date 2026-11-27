package com.ccar.forecasting.service.impl;

import com.ccar.common.error.CcarException;
import com.ccar.common.error.ErrorCode;
import com.ccar.forecasting.TestData;
import com.ccar.forecasting.config.ForecastingProperties;
import com.ccar.forecasting.hierarchy.HierarchyRepository;
import com.ccar.forecasting.hierarchy.HierarchyServiceClient;
import com.ccar.forecasting.hierarchy.RemoteHierarchyNode;
import com.ccar.forecasting.model.AggregateSummary;
import com.ccar.forecasting.model.DataSet;
import com.ccar.forecasting.model.DetailedBreakdown;
import com.ccar.forecasting.model.PeriodValues;
import com.ccar.forecasting.repository.FactDataRepository;
import com.ccar.forecasting.service.ForecastingService;
import com.iris.kdb.service.KdbTemplate;
import com.kx.c;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ForecastingServiceImplTest {

    private static final LocalDate T0 = LocalDate.of(2026, 9, 30);

    /** Fake ccar-hierarchy-service serving {@link TestData#buTree()}; records every call. */
    private static final class FakeHierarchyService extends HierarchyServiceClient {
        final List<String> calls = new ArrayList<>();

        FakeHierarchyService() {
            super((RestClient) null, 30);
        }

        @Override
        public Optional<RemoteHierarchyNode> getNode(String type, long nodeId, int depth) {
            calls.add(type + "/" + nodeId + "?depth=" + depth);
            return Optional.ofNullable(TestData.find(TestData.buTree(), nodeId, depth));
        }
    }

    /** Fake KDB: a fixed fact result; records every query. */
    private static final class FakeKdb extends KdbTemplate {
        final List<String> queries = new ArrayList<>();
        c.Flip facts = TestData.wideFacts(
                new long[]{1, 1, 2},
                new long[]{210001, 999999, 400001},
                "T0", new long[]{8504978, 1, 31685}, new int[]{3, 1, 1},
                "T_1", new long[]{8516417, 2, 0}, new int[]{3, 1, 0},
                "T_2", new long[]{0, 3, 31700}, new int[]{0, 1, 1});

        FakeKdb() {
            super(null);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T execute(String query) {
            queries.add(query);
            throw new IllegalArgumentException(query);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T execute(String query, Class<T> clazz) {
            queries.add(query);
            return (T) facts;
        }

        /** Aggregate-function calls, as {@code fn[arg1;arg2;...]}. */
        final List<String> calls = new ArrayList<>();
        c.Flip summary = TestData.accountFacts(new long[]{210001, 999999},
                "T0", new double[]{8504978, 1}, "T_1", new double[]{8516417, 2});

        @Override
        @SuppressWarnings("unchecked")
        public <T> T call(String function, Class<T> clazz, Object... args) {
            calls.add(function + "[" + String.join(";", Arrays.stream(args).map(String::valueOf).toList()) + "]");
            return (T) summary;
        }
    }

    private final FakeKdb kdb = new FakeKdb();
    private final FakeHierarchyService hierarchyService = new FakeHierarchyService();
    private final ForecastingProperties props = TestData.properties();
    private final ForecastingService service = new ForecastingServiceImpl(
            new HierarchyRepository(hierarchyService, props),
            new FactDataRepository(kdb, props), props);

    @Test
    void aggregateSummaryHasEveryConfiguredMeasureInOrderWithNullsForMissingData() {
        AggregateSummary s = service.aggregateSummary(DataSet.FORECAST, "bu", 1, "ALL", T0);

        assertEquals("BU", s.scope().type());
        assertEquals("ALL_BANK", s.scope().name());
        assertEquals(1, s.scope().level());
        assertEquals("ALL", s.portfolioId());
        assertEquals("USD", s.currency());
        assertEquals(37, s.periods().size()); // T0, T+1 .. T+36
        assertEquals(List.of("T0", "T+1", "T+2"), s.periods().subList(0, 3));

        assertNull(s.periodValuesActual()); // forecast only
        List<PeriodValues> rows = s.periodValuesForecast();
        assertEquals(List.of("210001", "400001", "500001", "999999"),
                rows.stream().map(PeriodValues::accountId).toList());
        assertEquals(List.of("Balance", "Revenue", "Expense", "999999"),
                rows.stream().map(PeriodValues::measure).toList());
        assertEquals(Arrays.asList(8504978.0, 8516417.0, null), rows.get(0).values().subList(0, 3));
        assertEquals(Arrays.asList(null, null, null), rows.get(1).values().subList(0, 3)); // no data for Revenue
        // one call of the forecast function, no query built here
        assertEquals(List.of("getAggregateForecast[BU;1;ALL;USD;2026-09-30]"), kdb.calls);
        assertTrue(kdb.queries.isEmpty());
        // the node comes from ccar-hierarchy-service (without children), never from a KDB hierarchy table
        assertEquals(List.of("BU/1?depth=0"), hierarchyService.calls);
    }

    @Test
    void actualsSummaryHasOnlyActualValuesAndLooksBackwards() {
        AggregateSummary a = service.aggregateSummary(DataSet.ACTUALS, "BU", 1, null, T0);

        assertEquals(List.of("T0", "T-1", "T-2"), a.periods().subList(0, 3));
        assertEquals(Arrays.asList(8504978.0, 8516417.0, null),
                a.periodValuesActual().get(0).values().subList(0, 3));
        assertNull(a.periodValuesForecast());
        // no portfolio = all portfolios, sent to the function as `ALL
        assertEquals(List.of("getAggregateActuals[BU;1;ALL;USD;2026-09-30]"), kdb.calls);
        assertTrue(kdb.queries.isEmpty());
    }

    @Test
    void varianceSummaryCallsTheActualsAndForecastFunctions() {
        AggregateSummary v = service.aggregateSummary(DataSet.VARIANCE, "BU", 1, "port_0001", T0);

        assertEquals(List.of("T0", "T-1", "T-2"), v.periods().subList(0, 3));
        assertEquals(List.of("210001", "400001", "500001", "999999"),
                v.periodValuesActual().stream().map(PeriodValues::accountId).toList());
        assertEquals(List.of("210001", "400001", "500001", "999999"),
                v.periodValuesForecast().stream().map(PeriodValues::accountId).toList());
        assertEquals(List.of("getAggregateActuals[BU;1;port_0001;USD;2026-09-30]",
                "getAggregateForecast[BU;1;port_0001;USD;2026-09-30]"), kdb.calls);
        assertTrue(kdb.queries.isEmpty()); // CCAR_VARIANCE is not read by the summary
        // one hierarchy-service call for both series
        assertEquals(List.of("BU/1?depth=0"), hierarchyService.calls);
    }

    @Test
    void detailedBreakdownPagesTheDirectChildrenAndQueriesOnlyThatPage() {
        DetailedBreakdown page0 = service.detailedBreakdown(DataSet.FORECAST, "BU", 1, "ALL", T0, 0, 1);
        assertEquals(List.of("BU/1?depth=1"), hierarchyService.calls);

        assertEquals(2, page0.totalElements());
        assertEquals(2, page0.totalPages());
        assertEquals(1, page0.items().size());
        var item = page0.items().get(0);
        assertEquals(2, item.id());
        assertEquals("North America", item.name());
        assertEquals("BU", item.hType());
        assertEquals(2, item.level());
        assertEquals(2, item.childCount());
        assertEquals(Arrays.asList(31685L, null, 31700L), item.dataRows().get(1).values().subList(0, 3));
        assertTrue(kdb.queries.get(kdb.queries.size() - 1).contains("where BU_ID_LEVEL2 in (),2]"));

        DetailedBreakdown page1 = service.detailedBreakdown(DataSet.FORECAST, "BU", 1, "ALL", T0, 1, 1);
        assertEquals(109, page1.items().get(0).id());
        // node 109 has no rows in the fake result: every measure is present, all null
        assertEquals(3, page1.items().get(0).dataRows().size());
        assertTrue(page1.items().get(0).dataRows().stream().allMatch(r -> r.values().stream().allMatch(v -> v == null)));
    }

    @Test
    void breakdownOfALeafOrBeyondTheLastPageIsEmptyWithoutQueryingFacts() {
        int before = countFactQueries();
        DetailedBreakdown leaf = service.detailedBreakdown(DataSet.FORECAST, "BU", 3, "ALL", T0, 0, null);
        DetailedBreakdown beyond = service.detailedBreakdown(DataSet.FORECAST, "BU", 1, "ALL", T0, 5, 25);

        assertEquals(0, leaf.totalElements());
        assertEquals(0, leaf.totalPages());
        assertEquals(25, leaf.size());
        assertTrue(leaf.items().isEmpty());
        assertTrue(beyond.items().isEmpty());
        assertEquals(2, beyond.totalElements());
        assertEquals(before, countFactQueries());
    }

    @Test
    void errors() {
        assertCode(ErrorCode.HIERARCHY_NODE_NOT_FOUND,
                () -> service.aggregateSummary(DataSet.FORECAST, "BU", 42, "ALL", T0));
        assertCode(ErrorCode.HIERARCHY_NOT_FOUND,
                () -> service.aggregateSummary(DataSet.FORECAST, "OPCODE", 1, "ALL", T0));
        assertCode(ErrorCode.INVALID_PARAMETER,
                () -> service.aggregateSummary(DataSet.FORECAST, "B U", 1, "ALL", T0));
        assertCode(ErrorCode.INVALID_PARAMETER,
                () -> service.aggregateSummary(DataSet.FORECAST, "BU", 1, "P1\";delete from `t", T0));
        assertCode(ErrorCode.INVALID_PARAMETER,
                () -> service.detailedBreakdown(DataSet.FORECAST, "BU", 1, "ALL", T0, -1, 10));
        assertCode(ErrorCode.INVALID_PARAMETER,
                () -> service.detailedBreakdown(DataSet.FORECAST, "BU", 1, "ALL", T0, 0, 501));
        // unsupported / malformed types and bad parameters are rejected before calling the hierarchy service
        assertEquals(List.of("BU/42?depth=0"), hierarchyService.calls);
    }


    private int countFactQueries() {
        return (int) kdb.queries.stream().filter(q -> q.startsWith("0!select T0")).count();
    }

    private static void assertCode(ErrorCode expected, org.junit.jupiter.api.function.Executable call) {
        assertEquals(expected, assertThrows(CcarException.class, call).getErrorCode());
    }
}
