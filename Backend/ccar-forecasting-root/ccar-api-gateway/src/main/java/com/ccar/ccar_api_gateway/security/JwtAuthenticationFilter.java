package com.ccar.ccar_api_gateway.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Logger log =
            LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenValidator jwtTokenValidator;

    public JwtAuthenticationFilter(JwtTokenValidator jwtTokenValidator) {
        this.jwtTokenValidator = jwtTokenValidator;
    }

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            GatewayFilterChain chain) {

        log.info("JWT FILTER EXECUTED: {} {}",
                exchange.getRequest().getMethod(),
                exchange.getRequest().getURI());

        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();

        log.debug("Checking JWT authentication for path: {}", path);

        // Public endpoints
        boolean isLogin = HttpMethod.POST.equals(method)
                && "/users/login".equals(path);

        boolean isRegistration = HttpMethod.POST.equals(method)
                && "/users".equals(path);

        if (isLogin || isRegistration) {
            log.debug("Public endpoint accessed: {}", path);
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest()
                .getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null ||
                !authHeader.startsWith("Bearer ")) {

            log.warn("Missing Bearer token for path: {}", path);
            return unauthorized(exchange);
        }

        String token = authHeader.substring(7).trim();

        if (token.isEmpty() || !jwtTokenValidator.isValid(token)) {
            log.warn("Invalid or expired JWT for path: {}", path);
            return unauthorized(exchange);
        }

        log.debug("JWT authentication successful for path: {}", path);

        return chain.filter(exchange);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
