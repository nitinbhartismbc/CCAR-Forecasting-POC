package com.ccar.ccar_api_gateway.error;

import com.ccar.ccar_api_gateway.logging.CorrelationIdWebFilter;
import com.ccar.common.error.ErrorCode;
import com.ccar.common.error.ProblemDetailFactory;
import com.ccar.common.tracing.CorrelationId;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Writes gateway-generated errors (401, 404 no route, 503/504 downstream failures, circuit open) in exactly the
 * same problem format as the downstream services, via the shared {@link ProblemDetailFactory}.
 */
@Component
public class ProblemResponseWriter {

    private final ObjectMapper objectMapper;
    private final String serviceName;
    private final long retryAfterSeconds;

    public ProblemResponseWriter(ObjectMapper objectMapper,
                                 @Value("${spring.application.name:ccar-api-gateway}") String serviceName,
                                 @Value("${ccar.gateway.retry-after-seconds:30}") long retryAfterSeconds) {
        // flattens ProblemDetail#properties to top-level members, as Spring MVC does in the services
        this.objectMapper = objectMapper.copy().addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class);
        this.serviceName = serviceName;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Mono<Void> write(ServerWebExchange exchange, ErrorCode code, String detail) {
        return write(exchange, code, detail, null);
    }

    /**
     * @param extraHeaders e.g. {@code WWW-Authenticate}; may be null
     */
    public Mono<Void> write(ServerWebExchange exchange, ErrorCode code, String detail, HttpHeaders extraHeaders) {
        ServerHttpResponse response = exchange.getResponse();
        String correlationId = CorrelationIdWebFilter.correlationId(exchange);
        String instance = CorrelationIdWebFilter.originalPath(exchange);

        ProblemDetail problem = ProblemDetailFactory.create(code, detail, instance, correlationId, serviceName);

        response.setStatusCode(HttpStatusCode.valueOf(code.status()));
        HttpHeaders headers = response.getHeaders();
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        headers.set(CorrelationId.HEADER, correlationId);
        if (code.status() == 503) {
            headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        }
        if (extraHeaders != null) {
            headers.putAll(extraHeaders);
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
