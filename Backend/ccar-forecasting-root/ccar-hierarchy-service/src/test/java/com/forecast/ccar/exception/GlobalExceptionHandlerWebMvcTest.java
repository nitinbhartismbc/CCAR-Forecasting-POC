package com.forecast.ccar.exception;

import com.ccar.common.error.CcarException;
import com.ccar.common.error.ErrorCode;
import com.forecast.ccar.controller.HierarchyController;
import com.forecast.ccar.model.HierarchyNode;
import com.forecast.ccar.service.HierarchyService;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * One test per handler mapping of ccar-hierarchy-service: KDB failures, BU errors, framework errors and the catch-all all
 * come back in the shared problem format with the request's correlation id.
 */
@WebMvcTest({HierarchyController.class})
class GlobalExceptionHandlerWebMvcTest {

    private static final String CID = "test-correlation-1";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private HierarchyService hierarchyService;

    static Stream<Arguments> kdbFailures() {
        return Stream.of(
                Arguments.of(new KdbConnectionException("Cannot connect to KDB at 192.168.11.200:5173"),
                        503, ErrorCode.KDB_UNAVAILABLE, true),
                Arguments.of(new KdbPoolExhaustedException("Timeout waiting for idle object"),
                        503, ErrorCode.KDB_POOL_EXHAUSTED, true),
                Arguments.of(new KdbTimeoutException("KDB did not answer"),
                        504, ErrorCode.KDB_TIMEOUT, false),
                Arguments.of(new KdbQueryException("KDB rejected the query: 'BU_HIERARCHY"),
                        502, ErrorCode.KDB_QUERY_FAILED, false),
                Arguments.of(new KdbException("Unexpected KDB client failure"),
                        503, ErrorCode.KDB_UNAVAILABLE, true));
    }

    @ParameterizedTest(name = "{0} -> {1} {2}")
    @MethodSource("kdbFailures")
    void kdbFailuresMapToStatusAndCode(KdbException failure, int status, ErrorCode code, boolean retryAfter)
            throws Exception {
        when(hierarchyService.getHierarchy(anyString(), any())).thenThrow(failure);

        MvcResult result = mvc.perform(get("/forecast/hierarchy/BU").header("X-Correlation-Id", CID))
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value(code.name()))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.type").value(code.type().toString()))
                .andExpect(jsonPath("$.instance").value("/forecast/hierarchy/BU"))
                .andExpect(jsonPath("$.correlationId").value(CID))
                .andExpect(jsonPath("$.service").value("ccar"))
                // internals (host, q error text) never reach the client
                .andExpect(jsonPath("$.detail").value(not(containsString("192.168"))))
                .andExpect(jsonPath("$.detail").value(not(containsString("BU_HIERARCHY"))))
                .andExpect(header().string("X-Correlation-Id", CID))
                .andReturn();

        assertThat(result.getResponse().getHeader("Retry-After")).isEqualTo(retryAfter ? "30" : null);
    }

    @Test
    void unknownNode_is404HierarchyNodeNotFound() throws Exception {
        when(hierarchyService.getNode(anyString(), anyLong(), any())).thenReturn(Optional.empty());

        mvc.perform(get("/forecast/hierarchy/geo/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("HIERARCHY_NODE_NOT_FOUND"))
                .andExpect(jsonPath("$.title").value("Hierarchy node not found"))
                .andExpect(jsonPath("$.detail").value("GEO node 999 not found"))
                .andExpect(jsonPath("$.hierarchyType").value("GEO"))
                .andExpect(jsonPath("$.nodeId").value(999))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void typeIsPassedToTheService() throws Exception {
        when(hierarchyService.getNode(eq("GEO"), eq(3L), eq(1)))
                .thenReturn(Optional.of(new HierarchyNode(3L, "United States", 3, 2L)));

        mvc.perform(get("/forecast/hierarchy/GEO/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.name").value("United States"));
    }

    @Test
    void unknownHierarchyType_is404HierarchyNotFound() throws Exception {
        when(hierarchyService.getHierarchy(eq("XYZ"), any())).thenThrow(
                new CcarException(ErrorCode.HIERARCHY_NOT_FOUND,
                        "No XYZ hierarchy exists (KDB table XYZ_HIERARCHY not found).").with("hierarchyType", "XYZ"));

        mvc.perform(get("/forecast/hierarchy/XYZ"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("HIERARCHY_NOT_FOUND"))
                .andExpect(jsonPath("$.hierarchyType").value("XYZ"));
    }

    @Test
    void unknownNodeChildren_is404HierarchyNodeNotFound() throws Exception {
        when(hierarchyService.getChildren(anyString(), anyLong())).thenReturn(Optional.empty());

        mvc.perform(get("/forecast/hierarchy/BU/5/children"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("HIERARCHY_NODE_NOT_FOUND"));
    }

    @Test
    void hierarchyUnavailable_is503WithRetryAfterFromException() throws Exception {
        when(hierarchyService.getHierarchy(anyString(), any())).thenThrow(
                new CcarException(ErrorCode.HIERARCHY_UNAVAILABLE, "not loaded", null, 12L));

        mvc.perform(get("/forecast/hierarchy/BU"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "12"))
                .andExpect(jsonPath("$.errorCode").value("HIERARCHY_UNAVAILABLE"));
    }

    @Test
    void invalidHierarchyData_is500WithGenericDetail() throws Exception {
        when(hierarchyService.getHierarchy(anyString(), any())).thenThrow(new CcarException(ErrorCode.HIERARCHY_INVALID,
                "The BU hierarchy data is invalid and could not be loaded.",
                new IllegalStateException("Invalid BU hierarchy row 6: PARENT_BU_ID is null")));

        mvc.perform(get("/forecast/hierarchy/BU"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("HIERARCHY_INVALID"))
                .andExpect(jsonPath("$.detail").value(not(containsString("row 6"))));
    }

    @Test
    void nonNumericPathVariable_is400InvalidParameter() throws Exception {
        mvc.perform(get("/forecast/hierarchy/BU/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"));
    }

    @Test
    void negativeDepth_is400InvalidParameter() throws Exception {
        mvc.perform(get("/forecast/hierarchy/BU").param("depth", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.parameter").value("depth"));
    }

    @Test
    void wrongMethod_is405MethodNotAllowed() throws Exception {
        mvc.perform(get("/forecast/hierarchy/BU/reload"))
                .andExpect(status().isBadRequest()); // GET matches /{type}/{nodeId} -> "reload" is not a number

        mvc.perform(post("/forecast/hierarchy/BU")) // /{type} is GET-only
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
        when(hierarchyService.getHierarchy(anyString(), any()))
                .thenThrow(new NullPointerException("secret internal state"));

        mvc.perform(get("/forecast/hierarchy/BU"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value(not(containsString("secret"))))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void missingOrUnsafeCorrelationIdIsReplacedAndEchoed() throws Exception {
        when(hierarchyService.getNode(anyString(), anyLong(), any())).thenReturn(Optional.empty());

        MvcResult result = mvc.perform(get("/forecast/hierarchy/BU/1").header("X-Correlation-Id", "bad id\r\nx"))
                .andExpect(status().isNotFound())
                .andReturn();

        String echoed = result.getResponse().getHeader("X-Correlation-Id");
        // not just doesNotContain("bad"): a generated UUID can contain the hex sequence "bad"
        assertThat(echoed).isNotBlank().doesNotContain("bad id").doesNotContain("\r").doesNotContain("\n");
        assertThat(result.getResponse().getContentAsString()).contains("\"correlationId\":\"" + echoed + "\"");
    }
}
