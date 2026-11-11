package com.ccar.common.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskDecorator;

/**
 * Wires correlation-id tracing and the shared error format into every Spring MVC service that has
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
}
