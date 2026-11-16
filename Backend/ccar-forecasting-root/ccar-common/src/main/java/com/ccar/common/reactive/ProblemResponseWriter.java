package com.ccar.common.reactive;

import com.ccar.common.error.ErrorCode;
import com.ccar.common.error.ProblemDetailFactory;
import com.ccar.common.tracing.CorrelationId;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Writes reactive-side errors (gateway 401, 404 no route, 503/504 downstream failures, circuit open) in exactly
 * the same problem format as the servlet services, via the shared {@link ProblemDetailFactory}.
 * Registered by {@link CcarReactiveAutoConfiguration}.
 */
public class ProblemResponseWriter {

    private final ObjectMapper objectMapper;
    private final String serviceName;
    private final long retryAfterSeconds;

    public ProblemResponseWriter(ObjectMapper objectMapper, String serviceName, long retryAfterSeconds) {
        // flattens ProblemDetail#properties to top-level members, as Spring MVC does in the services
        this.objectMapper = objectMapper.copy().addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class);
        this.serviceName = serviceName;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Mono<Void> write(ServerWebExchange exchange, ErrorCode code, String detail) {
        return write(exchange, code, detail, null);
    }

    /**
     * @param extraHeaders e.g. {@code WWW-Authenticate}; may be null. A plain map rather than {@code HttpHeaders}:
     *                     this class is compiled against Spring 7 but runs in the Spring 6 gateway, and
     *                     {@code HttpHeaders}' bulk methods differ between the two (only {@code set(String,
     *                     String)} and friends are binary compatible).
     */
    public Mono<Void> write(ServerWebExchange exchange, ErrorCode code, String detail,
                            Map<String, String> extraHeaders) {
        ServerHttpResponse response = exchange.getResponse();
        String correlationId = ReactiveCorrelation.correlationId(exchange);
        String instance = ReactiveCorrelation.originalPath(exchange);

        ProblemDetail problem = ProblemDetailFactory.create(code, detail, instance, correlationId, serviceName);

        response.setStatusCode(HttpStatusCode.valueOf(code.status()));
        HttpHeaders headers = response.getHeaders();
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        headers.set(CorrelationId.HEADER, correlationId);
        if (code.status() == 503) {
            headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        }
        if (extraHeaders != null) {
            extraHeaders.forEach(headers::set);
        }

        byte[] body = serialize(problem, code, correlationId);
        headers.setContentLength(body.length);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    private byte[] serialize(ProblemDetail problem, ErrorCode code, String correlationId) {
        try {
            return objectMapper.writeValueAsBytes(problem);
        } catch (JsonProcessingException e) {
            // never fail while reporting a failure
            String fallback = "{\"status\":" + code.status() + ",\"errorCode\":\"" + code.name()
                    + "\",\"correlationId\":\"" + correlationId + "\"}";
            return fallback.getBytes(StandardCharsets.UTF_8);
        }
    }
}
