package com.ccar.common.web;

import com.ccar.common.tracing.CorrelationId;
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Propagates the current correlation id on outbound service-to-service calls. Register it on any
 * {@code RestClient} / {@code RestTemplate}:
 * <pre>RestClient.builder().requestInterceptor(correlationIdClientInterceptor).build()</pre>
 */
public class CorrelationIdClientInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String correlationId = MDC.get(CorrelationId.MDC_KEY);
        if (correlationId != null && !request.getHeaders().containsHeader(CorrelationId.HEADER)) {
            request.getHeaders().set(CorrelationId.HEADER, correlationId);
        }
        return execution.execute(request, body);
    }
}
