package com.ccar.forecasting.hierarchy;

import com.ccar.common.error.CcarException;
import com.ccar.common.error.ErrorCode;
import com.ccar.common.error.ProblemDetailFactory;
import com.ccar.common.web.CorrelationIdClientInterceptor;
import com.ccar.forecasting.config.ForecastingProperties;
import com.ccar.forecasting.config.properties.HierarchyServiceEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * HTTP client of ccar-hierarchy-service. That service owns the hierarchy tables and keeps them cached, so this
 * service reads hierarchy nodes from it instead of querying KDB itself.
 * <p>
 * Error translation (the caller only ever sees {@link CcarException}s):
 * <ul>
 *   <li>404 HIERARCHY_NODE_NOT_FOUND -> {@link Optional#empty()} (the caller decides how to report it);</li>
 *   <li>HIERARCHY_* and KDB_* problems -> the same error code, detail and Retry-After, so the UI sees the real
 *       cause;</li>
 *   <li>any other 5xx, or the service not reachable -> 503 DOWNSTREAM_UNAVAILABLE; response too slow -> 504
 *       DOWNSTREAM_TIMEOUT;</li>
 *   <li>any other 4xx or an unreadable body -> 500, since it means the two services disagree on the API.</li>
 * </ul>
 */
@Component
public class HierarchyServiceClient {

    private static final Logger log = LoggerFactory.getLogger(HierarchyServiceClient.class);

    static final String SERVICE_NAME = "ccar-hierarchy-service";
    static final String NODE_PATH = "/forecast/hierarchy/{type}/{nodeId}?depth={depth}";

    /** Upstream codes passed through unchanged. */
    private static final Set<ErrorCode> PASSED_THROUGH = EnumSet.of(
            ErrorCode.HIERARCHY_NOT_FOUND, ErrorCode.HIERARCHY_UNAVAILABLE, ErrorCode.HIERARCHY_INVALID,
            ErrorCode.KDB_UNAVAILABLE, ErrorCode.KDB_POOL_EXHAUSTED, ErrorCode.KDB_TIMEOUT,
            ErrorCode.KDB_QUERY_FAILED);

    private final RestClient restClient;
    private final long retryAfterSeconds;

    @Autowired
    public HierarchyServiceClient(ForecastingProperties properties,
                                  ObjectProvider<CorrelationIdClientInterceptor> correlationId,
                                  @Value("${ccar.errors.retry-after-seconds:30}") long retryAfterSeconds) {
        this(restClient(properties.getHierarchyService(), correlationId), retryAfterSeconds);
    }

    /** For tests: any pre-built client whose base URL points at the service. */
    protected HierarchyServiceClient(RestClient restClient, long retryAfterSeconds) {
        this.restClient = restClient;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    private static RestClient restClient(HierarchyServiceEndpoint endpoint,
                                         ObjectProvider<CorrelationIdClientInterceptor> correlationId) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(endpoint.getConnectTimeoutMs()))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(http);
        requestFactory.setReadTimeout(Duration.ofMillis(endpoint.getReadTimeoutMs()));

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(endpoint.getBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE,
                        MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        // the request's correlation id travels to the hierarchy service, so one id ties both services' logs
        correlationId.ifAvailable(builder::requestInterceptor);
        return builder.build();
    }

    /**
     * The node {@code nodeId} of hierarchy {@code type} with {@code depth} levels of children below it
     * (0 = the node only, 1 = plus its direct children).
     *
     * @return empty if the hierarchy has no such node
     * @throws CcarException for every other failure, see the class comment
     */
    public Optional<RemoteHierarchyNode> getNode(String type, long nodeId, int depth) {
        try {
            return restClient.get()
                    .uri(NODE_PATH, type, nodeId, depth)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == 200) {
                            RemoteHierarchyNode node = response.bodyTo(RemoteHierarchyNode.class);
                            if (node == null || node.id() == null || node.levelId() == null) {
                                throw invalidResponse(type, "empty or incomplete node", null);
                            }
                            return Optional.of(node);
                        }
                        Map<String, Object> problem = readProblem(response);
                        ErrorCode code = errorCode(problem.get(ProblemDetailFactory.ERROR_CODE));
                        if (status == 404 && code == ErrorCode.HIERARCHY_NODE_NOT_FOUND) {
                            return Optional.empty();
                        }
                        throw translate(type, status, code, problem.get("detail"),
                                response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
                    });
        } catch (ResourceAccessException e) {
            throw unreachable(type, e);
        } catch (RestClientException e) {
            throw invalidResponse(type, e.getMessage(), e);
        }
    }

    private CcarException translate(String type, int status, ErrorCode code, Object detail, String retryAfter) {
        log.warn("{} answered HTTP {} ({}) for {} hierarchy: {}", SERVICE_NAME, status, code, type, detail);
        if (code != null && PASSED_THROUGH.contains(code)) {
            String text = detail instanceof String s && !s.isBlank() ? s : code.title();
            return new CcarException(code, text, null, parseRetryAfter(retryAfter))
                    .with("hierarchyType", type);
        }
        if (status == 504) {
            return new CcarException(ErrorCode.DOWNSTREAM_TIMEOUT,
                    "The hierarchy service did not answer in time.").with("hierarchyType", type);
        }
        if (status >= 500) {
            Long wait = parseRetryAfter(retryAfter);
            return new CcarException(ErrorCode.DOWNSTREAM_UNAVAILABLE, "The hierarchy service is unavailable.",
                    null, wait != null ? wait : retryAfterSeconds).with("hierarchyType", type);
        }
        return new CcarException(ErrorCode.INTERNAL_ERROR,
                "The hierarchy service rejected the request (HTTP " + status + ").").with("hierarchyType", type);
    }

    private CcarException unreachable(String type, ResourceAccessException e) {
        Throwable cause = e.getMostSpecificCause();
        boolean readTimeout = cause instanceof HttpTimeoutException && !(cause instanceof HttpConnectTimeoutException);
        log.warn("{} call for {} hierarchy failed: {}", SERVICE_NAME, type, cause.toString());
        if (readTimeout) {
            return new CcarException(ErrorCode.DOWNSTREAM_TIMEOUT,
                    "The hierarchy service did not answer in time.", e).with("hierarchyType", type);
        }
        return new CcarException(ErrorCode.DOWNSTREAM_UNAVAILABLE, "The hierarchy service is unavailable.", e,
                retryAfterSeconds).with("hierarchyType", type);
    }

    private static CcarException invalidResponse(String type, String why, Throwable cause) {
        log.error("Unreadable {} response for {} hierarchy: {}", SERVICE_NAME, type, why);
        return new CcarException(ErrorCode.HIERARCHY_INVALID,
                "The " + type + " hierarchy data could not be read from the hierarchy service.", cause)
                .with("hierarchyType", type);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readProblem(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response) {
        try {
            Map<String, Object> body = response.bodyTo(Map.class);
            return body == null ? Map.of() : body;
        } catch (Exception e) { // not JSON (e.g. a proxy's HTML error page): status alone decides
            return Map.of();
        }
    }

    private static ErrorCode errorCode(Object value) {
        if (value instanceof String s) {
            try {
                return ErrorCode.valueOf(s);
            } catch (IllegalArgumentException ignored) {
                // a code this build does not know yet
            }
        }
        return null;
    }

    private static Long parseRetryAfter(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Math.max(1, Long.parseLong(value.trim()));
        } catch (NumberFormatException e) {
            return null; // HTTP-date form: not used by the CCAR services
        }
    }
}
