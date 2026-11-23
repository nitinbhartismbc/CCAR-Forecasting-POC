package com.ccar.forecasting.hierarchy;

import com.ccar.common.error.CcarException;
import com.ccar.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HierarchyServiceClientTest {

    private static final String BASE = "http://hierarchy/ccar";

    private MockRestServiceServer server;
    private HierarchyServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HierarchyServiceClient(builder.build(), 30);
    }

    @Test
    void readsTheNodeWithItsChildren() {
        server.expect(requestTo(BASE + "/forecast/hierarchy/BU/1?depth=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"id":1,"name":"ALL_BANK","levelId":1,"parentId":null,"childCount":2,"children":[
                          {"id":2,"name":"North America","levelId":2,"parentId":1,"childCount":2,"children":[]},
                          {"id":109,"name":"Europe","levelId":2,"parentId":1,"childCount":1,"children":[]}],
                         "someNewField":"ignored"}
                        """, MediaType.APPLICATION_JSON));

        RemoteHierarchyNode node = client.getNode("BU", 1, 1).orElseThrow();

        assertEquals(1L, node.id());
        assertEquals("ALL_BANK", node.name());
        assertEquals(1, node.levelId());
        assertNull(node.parentId());
        assertEquals(2, node.childCount());
        assertEquals(2, node.children().size());
        assertEquals("Europe", node.children().get(1).name());
        assertEquals(2, node.children().get(0).childCount());
        server.verify();
    }

    @Test
    void unknownNodeIsEmpty() {
        server.expect(requestTo(BASE + "/forecast/hierarchy/BU/42?depth=0"))
                .andRespond(problem(HttpStatus.NOT_FOUND, "HIERARCHY_NODE_NOT_FOUND", "BU node 42 not found"));

        assertEquals(Optional.empty(), client.getNode("BU", 42, 0));
    }

    @Test
    void hierarchyErrorsArePassedThroughWithRetryAfter() {
        server.expect(requestTo(BASE + "/forecast/hierarchy/GEO/1?depth=0"))
                .andRespond(problem(HttpStatus.SERVICE_UNAVAILABLE, "HIERARCHY_UNAVAILABLE", "GEO not loaded yet")
                        .header(HttpHeaders.RETRY_AFTER, "12"));

        CcarException e = assertThrows(CcarException.class, () -> client.getNode("GEO", 1, 0));
        assertEquals(ErrorCode.HIERARCHY_UNAVAILABLE, e.getErrorCode());
        assertEquals("GEO not loaded yet", e.getMessage());
        assertEquals(12L, e.getRetryAfterSeconds());
        assertEquals("GEO", e.getProperties().get("hierarchyType"));
    }

    @Test
    void unknownTypeOnTheHierarchyServiceIsHierarchyNotFound() {
        server.expect(requestTo(BASE + "/forecast/hierarchy/BU/1?depth=0"))
                .andRespond(problem(HttpStatus.NOT_FOUND, "HIERARCHY_NOT_FOUND", "No BU hierarchy exists"));

        assertCode(ErrorCode.HIERARCHY_NOT_FOUND, () -> client.getNode("BU", 1, 0));
    }

    @Test
    void otherServerErrorsAreDownstreamUnavailable() {
        server.expect(requestTo(BASE + "/forecast/hierarchy/BU/1?depth=0"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("<html>proxy error</html>")
                        .contentType(MediaType.TEXT_HTML));

        CcarException e = assertThrows(CcarException.class, () -> client.getNode("BU", 1, 0));
        assertEquals(ErrorCode.DOWNSTREAM_UNAVAILABLE, e.getErrorCode());
        assertEquals(30L, e.getRetryAfterSeconds());
    }

    @Test
    void unexpectedClientErrorIsInternal() {
        server.expect(requestTo(BASE + "/forecast/hierarchy/BU/1?depth=0"))
                .andRespond(problem(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "No endpoint"));

        assertCode(ErrorCode.INTERNAL_ERROR, () -> client.getNode("BU", 1, 0));
    }

    @Test
    void connectionFailureIsDownstreamUnavailable() {
        server.expect(requestTo(BASE + "/forecast/hierarchy/BU/1?depth=0"))
                .andRespond(withException(new IOException("Connection refused")));

        assertCode(ErrorCode.DOWNSTREAM_UNAVAILABLE, () -> client.getNode("BU", 1, 0));
    }

    @Test
    void readTimeoutIsDownstreamTimeout() {
        server.expect(requestTo(BASE + "/forecast/hierarchy/BU/1?depth=0"))
                .andRespond(withException(new HttpTimeoutException("request timed out")));

        assertCode(ErrorCode.DOWNSTREAM_TIMEOUT, () -> client.getNode("BU", 1, 0));
    }

    @Test
    void incompleteBodyIsHierarchyInvalid() {
        server.expect(requestTo(BASE + "/forecast/hierarchy/BU/1?depth=0"))
                .andRespond(withSuccess("{\"name\":\"no id\"}", MediaType.APPLICATION_JSON));

        assertCode(ErrorCode.HIERARCHY_INVALID, () -> client.getNode("BU", 1, 0));
    }

    private static org.springframework.test.web.client.response.DefaultResponseCreator problem(
            HttpStatus status, String code, String detail) {
        return withStatus(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body("""
                {"type":"urn:ccar:error:x","status":%d,"detail":"%s","errorCode":"%s",
                 "service":"ccar","correlationId":"c-1"}
                """.formatted(status.value(), detail, code));
    }

    private static void assertCode(ErrorCode expected, org.junit.jupiter.api.function.Executable call) {
        assertEquals(expected, assertThrows(CcarException.class, call).getErrorCode());
    }
}
