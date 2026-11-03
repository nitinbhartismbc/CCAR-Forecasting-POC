package com.iris.kdb.logging;

import com.iris.kdb.config.KdbAutoConfiguration;
import com.iris.kdb.config.KdbProperties;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Registers {@link KdbLoggingAspect} only when AspectJ is on the consuming app's classpath
 * and {@code kdb.logging.enabled} is not false. Apps without AOP are unaffected.
 */
@AutoConfiguration(after = KdbAutoConfiguration.class)
@ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
@ConditionalOnProperty(prefix = "kdb.logging", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(KdbProperties.class)
public class KdbLoggingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public KdbLoggingAspect kdbLoggingAspect(KdbProperties properties) {
        KdbProperties.Logging cfg = properties.getLogging();
        return new KdbLoggingAspect(cfg.getSlowQueryThresholdMs(), cfg.getMaxQueryLength());
    }
}
