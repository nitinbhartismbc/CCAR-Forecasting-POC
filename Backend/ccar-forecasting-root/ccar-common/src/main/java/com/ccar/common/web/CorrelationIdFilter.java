package com.ccar.common.web;

import com.ccar.common.tracing.CorrelationId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Outermost servlet filter of every CCAR service. Reads {@code X-Correlation-Id} (set by the api-gateway or
 * the UI), or generates one when it is missing / not log-safe, then
 * <ul>
 *   <li>puts it in the SLF4J MDC so every log line of the request carries it,</li>
 *   <li>stores it as a request attribute so it survives into the error dispatch (MDC is cleared by then),</li>
 *   <li>echoes it on the response - success and error alike.</li>
 * </ul>
 */
public class CorrelationIdFilter extends OncePerRequestFilter implements Ordered {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String correlationId = CorrelationId.resolve(request.getHeader(CorrelationId.HEADER));

        request.setAttribute(CorrelationId.ATTRIBUTE, correlationId);
        response.setHeader(CorrelationId.HEADER, correlationId);
        MDC.put(CorrelationId.MDC_KEY, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    /** Correlation id of the current request: MDC first, then the request attribute, else null. */
    public static String current(WebRequest request) {
        String fromMdc = MDC.get(CorrelationId.MDC_KEY);
        if (fromMdc != null) {
            return fromMdc;
        }
        Object attr = request == null ? null
                : request.getAttribute(CorrelationId.ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        return attr != null ? attr.toString() : null;
    }
}
