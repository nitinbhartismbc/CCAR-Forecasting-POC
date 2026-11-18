package com.ccar.forecasting.controller;

import com.ccar.common.error.CcarException;
import com.ccar.common.error.ErrorCode;
import com.ccar.forecasting.model.AggregateSummary;
import com.ccar.forecasting.model.BreakdownItem;
import com.ccar.forecasting.model.DataSet;
import com.ccar.forecasting.model.DetailedBreakdown;
import com.ccar.forecasting.model.HierarchyNode;
import com.ccar.forecasting.model.PeriodValues;
import com.ccar.forecasting.model.Scope;
import com.ccar.forecasting.service.ForecastingService;
import com.iris.kdb.exception.KdbTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** JSON field names / shapes the UI relies on, request binding, and errors in the shared problem format. */
@WebMvcTest({ForecastingController.class, ReferenceDataController.class})
class ForecastingControllerWebMvcTest {

    private static final LocalDate T0 = LocalDate.of(2026, 9, 30);
    private static final Scope SCOPE = new Scope("BU", 1, "ALL_BANK", 1);
    private static final List<PeriodValues> ROWS =
            List.of(new PeriodValues("210001", "Balance", Arrays.asList(8504978L, null)));

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ForecastingService service;

    @Test
    void aggregateSummary() throws Exception {
        when(service.aggregateSummary(DataSet.ACTUALS, "BU", 1, "ALL", T0)).thenReturn(
                new AggregateSummary(SCOPE, T0, "ALL", "USD", List.of("T0", "T-1"), ROWS));

        mvc.perform(get("/v1/actuals/aggregate-summary")
                        .param("hierarchyType", "BU").param("hierarchyId", "1")
                        .param("portfolioId", "ALL").param("t0Date", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope.type").value("BU"))
                .andExpect(jsonPath("$.scope.name").value("ALL_BANK"))
                .andExpect(jsonPath("$.t0Date").value("2026-09-30"))
                .andExpect(jsonPath("$.periods[1]").value("T-1"))
                .andExpect(jsonPath("$.period_values[0].accountId").value("210001"))
                .andExpect(jsonPath("$.period_values[0].measure").value("Balance"))
                .andExpect(jsonPath("$.period_values[0].values[0]").value(8504978))
                .andExpect(jsonPath("$.period_values[0].values[1]").value(nullValue()));
    }

    @Test
    void detailedBreakdownDefaultsPortfolioAndPaging() throws Exception {
        when(service.detailedBreakdown(eq(DataSet.FORECAST), eq("BU"), eq(1L), isNull(), eq(T0), eq(0), isNull()))
                .thenReturn(new DetailedBreakdown(SCOPE, T0, "ALL", "USD", List.of("T0", "T+1"), 0, 25, 1, 1,
                        List.of(new BreakdownItem(2, "North America", "BU", 2, 2, ROWS))));

        mvc.perform(get("/v1/forecast/detailed-breakdown")
                        .param("hierarchyType", "BU").param("hierarchyId", "1").param("t0Date", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].hType").value("BU"))
                .andExpect(jsonPath("$.items[0].childCount").value(2))
                .andExpect(jsonPath("$.items[0].data_rows[0].measure").value("Balance"));
        verify(service).detailedBreakdown(eq(DataSet.FORECAST), eq("BU"), eq(1L), isNull(), eq(T0), eq(0), isNull());
    }

    @Test
    void hierarchyRootIsFlatWithLeafFlag() throws Exception {
        when(service.hierarchy("BU")).thenReturn(List.of(new HierarchyNode(1, "ALL_BANK", 1, null, 1),
                new HierarchyNode(2, "North America", 2, 1L, 0)));

        mvc.perform(get("/v1/hierarchy/root/BU"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].parentId").value(nullValue()))
                .andExpect(jsonPath("$[0].isLeafNode").value(false))
                .andExpect(jsonPath("$[1].level").value(2))
                .andExpect(jsonPath("$[1].parentId").value(1))
                .andExpect(jsonPath("$[1].isLeafNode").value(true));
    }

    @Test
    void portfoliosAndDates() throws Exception {
        when(service.t0Dates()).thenReturn(List.of("2026-09-30", "2025-12-31"));
        mvc.perform(get("/v1/t0-dates")).andExpect(jsonPath("$[1]").value("2025-12-31"));
    }

    @Test
    void badDateIs400() throws Exception {
        mvc.perform(get("/v1/forecast/aggregate-summary")
                        .param("hierarchyType", "BU").param("hierarchyId", "1").param("t0Date", "30/09/2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").exists());
    }

    @Test
    void missingParameterIs400() throws Exception {
        mvc.perform(get("/v1/forecast/aggregate-summary").param("hierarchyType", "BU").param("hierarchyId", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownDataSetIs404() throws Exception {
        mvc.perform(get("/v1/budget/aggregate-summary")
                        .param("hierarchyType", "BU").param("hierarchyId", "1").param("t0Date", "2026-09-30"))
                .andExpect(status().isNotFound());
    }

    @Test
    void serviceErrorsUseTheSharedProblemFormat() throws Exception {
        when(service.aggregateSummary(any(), anyString(), anyLong(), any(), any()))
                .thenThrow(new CcarException(ErrorCode.HIERARCHY_NODE_NOT_FOUND, "BU node 42 not found"));
        mvc.perform(get("/v1/forecast/aggregate-summary")
                        .param("hierarchyType", "BU").param("hierarchyId", "42").param("t0Date", "2026-09-30"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("HIERARCHY_NODE_NOT_FOUND"));

        when(service.detailedBreakdown(any(), anyString(), anyLong(), any(), any(), anyInt(), any()))
                .thenThrow(new KdbTimeoutException("timeout"));
        mvc.perform(get("/v1/variance/detailed-breakdown")
                        .param("hierarchyType", "BU").param("hierarchyId", "1").param("t0Date", "2026-09-30"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.errorCode").value("KDB_TIMEOUT"));
    }
}
