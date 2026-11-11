package com.ccar.ccar_api_gateway.security;

import com.ccar.ccar_api_gateway.error.ProblemResponseWriter;
import com.ccar.ccar_api_gateway.logging.CorrelationIdWebFilter;
import com.ccar.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Rejects routed requests without a valid Bearer token with a 401 in the shared problem format and an
 * RFC 6750 {@code WWW-Authenticate} header, distinguishing a missing token (UNAUTHORIZED), an expired one
 * (TOKEN_EXPIRED - the UI should send the user to log in again) and an invalid one (TOKEN_INVALID).
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Logger log =
            LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String REALM = "Bearer realm=\"ccar\"";

    private final JwtTokenValidator jwtTokenValidator;
    private final ProblemResponseWriter problemWriter;

    public JwtAuthenticationFilter(JwtTokenValidator jwtTokenValidator, ProblemResponseWriter problemWriter) {
        this.jwtTokenValidator = jwtTokenValidator;
        this.problemWriter = problemWriter;
    }

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            GatewayFilterChain chain) {

        String correlationId = CorrelationIdWebFilter.correlationId(exchange);
        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();

        log.debug("[{}] Checking JWT authentication for {} {}", correlationId, method, path);

        // Public endpoints
        boolean isLogin = HttpMethod.POST.equals(method)
                && "/users/login".equals(path);

        boolean isRegistration = HttpMethod.POST.equals(method)
                && "/users".equals(path);

        if (isLogin || isRegistration) {
            log.debug("[{}] Public endpoint accessed: {}", correlationId, path);
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest()
                .getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null ||
                !authHeader.startsWith("Bearer ")) {

            log.warn("[{}] Missing Bearer token for path: {}", correlationId, path);
            return reject(exchange, ErrorCode.UNAUTHORIZED,
                    "A Bearer access token is required.", REALM);
        }

        String token = authHeader.substring(7).trim();
        JwtTokenValidator.TokenStatus status = token.isEmpty()
                ? JwtTokenValidator.TokenStatus.INVALID
                : jwtTokenValidator.validate(token);

        switch (status) {
            case EXPIRED -> {
                log.warn("[{}] Expired JWT for path: {}", correlationId, path);
                return reject(exchange, ErrorCode.TOKEN_EXPIRED,
                        "The access token has expired. Please sign in again.",
                        REALM + ", error=\"invalid_token\", error_description=\"The access token expired\"");
            }
            case INVALID -> {
                log.warn("[{}] Invalid JWT for path: {}", correlationId, path);
                return reject(exchange, ErrorCode.TOKEN_INVALID,
                        "The access token is invalid.",
                        REALM + ", error=\"invalid_token\", error_description=\"The access token is invalid\"");
            }
            default -> {
                log.debug("[{}] JWT authentication successful for path: {}", correlationId, path);
                return chain.filter(exchange);
            }
        }
    }

    private Mono<Void> reject(ServerWebExchange exchange, ErrorCode code, String detail, String wwwAuthenticate) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.WWW_AUTHENTICATE, wwwAuthenticate);
        return problemWriter.write(exchange, code, detail, headers);
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
