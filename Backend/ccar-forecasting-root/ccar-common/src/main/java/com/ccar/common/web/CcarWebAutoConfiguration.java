package com.ccar.common.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;

/**
 * Wires correlation-id tracing, the shared error format and the shared {@link GlobalExceptionHandler} into every
 * Spring MVC service that has
 * ccar-common on its classpath. Ignored by the reactive api-gateway (it has its own WebFlux equivalents).
 * Runs before Boot's ErrorMvcAutoConfiguration so our {@link ErrorAttributes} wins.
 */
@AutoConfiguration(beforeName = "org.springframework.boot.webmvc.autoconfigure.error.ErrorMvcAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = {"jakarta.servlet.Filter", "org.springframework.web.servlet.DispatcherServlet"})
public class CcarWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }

    @Bean
    @ConditionalOnMissingBean(ErrorAttributes.class)
    public CcarErrorAttributes ccarErrorAttributes(@Value("${spring.application.name:unknown}") String service) {
        return new CcarErrorAttributes(service);
    }

    @Bean
    @ConditionalOnMissingBean
    public CorrelationIdClientInterceptor correlationIdClientInterceptor() {
        return new CorrelationIdClientInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean(TaskDecorator.class)
    public MdcTaskDecorator mdcTaskDecorator() {
        return new MdcTaskDecorator();
    }

    /** The shared exception handler; services no longer declare their own {@code @RestControllerAdvice}. */
    @Bean
    @ConditionalOnMissingBean(GlobalExceptionHandler.class)
    public GlobalExceptionHandler ccarGlobalExceptionHandler(
            @Value("${spring.application.name:unknown}") String service, ObjectProvider<CcarErrorMapper> mappers) {
        return new GlobalExceptionHandler(service, mappers.orderedStream().toList());
    }

    // Store-specific mappings: each only when its library is on the service's classpath (nested classes so the
    // missing types are never loaded).

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.iris.kdb.exception.KdbException")
    static class KdbErrorMapping {

        @Bean
        @ConditionalOnMissingBean
        KdbErrorMapper kdbErrorMapper(@Value("${ccar.errors.retry-after-seconds:30}") long retryAfterSeconds) {
            return new KdbErrorMapper(retryAfterSeconds);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.dao.DataAccessException")
    static class PersistenceErrorMapping {

        @Bean
        @ConditionalOnMissingBean
        PersistenceErrorMapper persistenceErrorMapper() {
            return new PersistenceErrorMapper();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "jakarta.validation.ConstraintViolationException")
    static class ConstraintViolationErrorMapping {

        @Bean
        @ConditionalOnMissingBean
        ConstraintViolationErrorMapper constraintViolationErrorMapper() {
            return new ConstraintViolationErrorMapper();
        }
    }
}
