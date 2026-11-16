package com.ccar.common.reactive;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * Wires the shared error handling into every reactive (WebFlux) CCAR app - today the api-gateway: the
 * {@link ReactiveGlobalExceptionHandler} and the {@link ProblemResponseWriter} that the gateway's JWT filter and
 * circuit-breaker fallback also use. Ignored by the servlet services (they get
 * {@code com.ccar.common.web.CcarWebAutoConfiguration}).
 * <p>
 * Properties: {@code ccar.errors.retry-after-seconds} (default 30) - {@code Retry-After} on 503s.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass(name = {"reactor.core.publisher.Mono", "com.fasterxml.jackson.databind.ObjectMapper"})
public class CcarReactiveAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ProblemResponseWriter problemResponseWriter(ObjectProvider<ObjectMapper> objectMapper,
                                                       @Value("${spring.application.name:unknown}") String service,
                                                       @Value("${ccar.errors.retry-after-seconds:30}") long retryAfter) {
        return new ProblemResponseWriter(objectMapper.getIfAvailable(ObjectMapper::new), service, retryAfter);
    }

    @Bean
    @ConditionalOnMissingBean
    public ReactiveGlobalExceptionHandler reactiveGlobalExceptionHandler(ProblemResponseWriter writer) {
        return new ReactiveGlobalExceptionHandler(writer);
    }
}
