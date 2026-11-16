package com.ccar.common.logging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Registers the shared {@link LoggingAspect} in every CCAR service (servlet and reactive) that has ccar-common
 * and AspectJ on its classpath, unless {@code ccar.logging.enabled=false}.
 * <p>
 * The condition is the same class Boot's AopAutoConfiguration checks before enabling {@code @Aspect} proxying,
 * so the aspect is only registered when it will actually be applied (services add
 * {@code spring-boot-starter-aspectj} on Boot 4, {@code spring-boot-starter-aop} on Boot 3).
 * <p>
 * Properties: {@code ccar.logging.enabled} (default true), {@code ccar.logging.max-length} (default 500,
 * truncation of DEBUG args/results).
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.aspectj.weaver.Advice")
@ConditionalOnProperty(prefix = "ccar.logging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CcarLoggingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public LoggingAspect ccarLoggingAspect(
            @Value("${ccar.logging.max-length:" + LoggingAspect.DEFAULT_MAX_LOG_LENGTH + "}") int maxLength) {
        return new LoggingAspect(maxLength);
    }
}
