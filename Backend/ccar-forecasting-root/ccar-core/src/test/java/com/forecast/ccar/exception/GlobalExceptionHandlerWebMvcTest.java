package com.forecast.ccar.exception;

import com.ccar.common.error.CcarException;
import com.ccar.common.error.ErrorCode;
import com.forecast.ccar.controller.CcarForecastController;
import com.forecast.ccar.service.BuHierarchyService;
import com.forecast.ccar.service.KdbService;
import com.iris.kdb.exception.KdbConnectionException;
import com.iris.kdb.exception.KdbException;
import com.iris.kdb.exception.KdbPoolExhaustedException;
import com.iris.kdb.exception.KdbQueryException;
import com.iris.kdb.exception.KdbTimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * One test per handler mapping of ccar-core: KDB failures, BU errors, framework errors and the catch-all all
 * come back in the shared problem format with the request's correlation id.
 */
@WebMvcTest(CcarForecastController.class)
class GlobalExceptionHandlerWebMvcTest {

    private static final String CID = "test-correlation-1";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private KdbService kdbService;

    @MockitoBean
    private BuHierarchyService hierarchyService;

    static Stream<Arguments> kdbFailures() {
        return Stream.of(
                Arguments.of(new KdbConnectionException("Cannot connect to KDB at 192.168.11.200:5173"),
                        503, ErrorCode.KDB_UNAVAILABLE, true),
                Arguments.of(new KdbPoolExhaustedException("Timeout waiting for idle object"),
                        503, ErrorCode.KDB_POOL_EXHAUSTED, true),
                Arguments.of(new KdbTimeoutException("KDB did not answer"),
                        504, ErrorCode.KDB_TIMEOUT, false),
                Arguments.of(new KdbQueryException("KDB rejected the query: 'forecast_ccar"),
                        502, ErrorCode.KDB_QUERY_FAILED, false),
                Arguments.of(new KdbException("Unexpected KDB client failure"),
                        503, ErrorCode.KDB_UNAVAILABLE, true));
    }

    @ParameterizedTest(name = "{0} -> {1} {2}")
    @MethodSource("kdbFailures")
    void kdbFailuresMapToStatusAndCode(KdbException failure, int status, ErrorCode code, boolean retryAfter)
            throws Exception {
        when(kdbService.getForecastCcarJson()).thenThrow(failure);

        MvcResult result = mvc.perform(get("/forecast/forecast-ccar").header("X-Correlation-Id", CID))
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value(code.name()))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.type").value(code.type().toString()))
                .andExpect(jsonPath("$.instance").value("/forecast/forecast-ccar"))
                .andExpect(jsonPath("$.correlationId").value(CID))
                .andExpect(jsonPath("$.service").value("ccar"))
                // internals (host, q error text) never reach the client
                .andExpect(jsonPath("$.detail").value(not(containsString("192.168"))))
                .andExpect(jsonPath("$.detail").value(not(containsString("forecast_ccar"))))
                .andExpect(header().string("X-Correlation-Id", CID))
                .andReturn();

        assertThat(result.getResponse().getHeader("Retry-After")).isEqualTo(retryAfter ? "30" : null);
    }

    @Test
    void unknownBu_is404BuNotFound() throws Exception {
        when(hierarchyService.getNode(anyLong(), any())).thenReturn(Optional.empty());

        mvc.perform(get("/forecast/bu-hierarchy/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BU_NOT_FOUND"))
                .andExpect(jsonPath("$.title").value("Business unit not found"))
                .andExpect(jsonPath("$.detail").value("BU 999 not found"))
                .andExpect(jsonPath("$.buId").value(999))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void unknownBuChildren_is404BuNotFound() throws Exception {
        when(hierarchyService.getChildren(anyLong())).thenReturn(Optional.empty());

        mvc.perform(get("/forecast/bu-hierarchy/5/children"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BU_NOT_FOUND"));
    }

    @Test
    void hierarchyUnavailable_is503WithRetryAfterFromException() throws Exception {
        when(hierarchyService.getHierarchy(any())).thenThrow(
                new CcarException(ErrorCode.BU_HIERARCHY_UNAVAILABLE, "not loaded", null, 12L));

        mvc.perform(get("/forecast/bu-hierarchy"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "12"))
                .andExpect(jsonPath("$.errorCode").value("BU_HIERARCHY_UNAVAILABLE"));
    }

    @Test
    void invalidHierarchyData_is500WithGenericDetail() throws Exception {
        when(hierarchyService.getHierarchy(any())).thenThrow(new CcarException(ErrorCode.BU_HIERARCHY_INVALID,
                "The BU hierarchy data is invalid and could not be loaded.",
                new IllegalStateException("Invalid BU hierarchy row 6: BU_ID_LEVEL2 is null")));

        mvc.perform(get("/forecast/bu-hierarchy"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("BU_HIERARCHY_INVALID"))
                .andExpect(jsonPath("$.detail").value(not(containsString("row 6"))));
    }

    @Test
    void nonNumericPathVariable_is400InvalidParameter() throws Exception {
        mvc.perform(get("/forecast/bu-hierarchy/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"));
    }

    @Test
    void negativeDepth_is400InvalidParameter() throws Exception {
        mvc.perform(get("/forecast/bu-hierarchy").param("depth", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.parameter").value("depth"));
    }

    @Test
    void wrongMethod_is405MethodNotAllowed() throws Exception {
        mvc.perform(get("/forecast/bu-hierarchy/reload"))
                .andExpect(status().isBadRequest()); // GET matches /{buId} -> "reload" is not a number

        mvc.perform(post("/forecast/forecast-ccar"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void unknownPath_is404ResourceNotFound() throws Exception {
        mvc.perform(get("/forecast/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void unexpectedException_is500WithoutLeakingInternals() throws Exception {
        when(kdbService.getForecastCcarJson()).thenThrow(new NullPointerException("secret internal state"));

        mvc.perform(get("/forecast/forecast-ccar"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value(not(containsString("secret"))))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void missingOrUnsafeCorrelationIdIsReplacedAndEchoed() throws Exception {
        when(hierarchyService.getNode(anyLong(), any())).thenReturn(Optional.empty());

        MvcResult result = mvc.perform(get("/forecast/bu-hierarchy/1").header("X-Correlation-Id", "bad id\r\nx"))
                .andExpect(status().isNotFound())
                .andReturn();

        String echoed = result.getResponse().getHeader("X-Correlation-Id");
        assertThat(echoed).isNotBlank().doesNotContain("bad");
        assertThat(result.getResponse().getContentAsString()).contains("\"correlationId\":\"" + echoed + "\"");
    }
}
