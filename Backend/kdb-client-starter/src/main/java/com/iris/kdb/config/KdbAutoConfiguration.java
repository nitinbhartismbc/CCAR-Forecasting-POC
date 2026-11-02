package com.iris.kdb.config;

import com.iris.kdb.controller.KdbMetricsController;
import com.iris.kdb.metrics.KdbPoolMetrics;
import com.iris.kdb.pool.KdbConnectionPool;
import com.iris.kdb.service.KdbTemplate;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties(KdbProperties.class)
@EnableScheduling
public class KdbAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public KdbConnectionPool kdbConnectionPool(
            KdbProperties properties) {

        return new KdbConnectionPool(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public KdbTemplate kdbTemplate(
            KdbConnectionPool connectionPool) {

        return new KdbTemplate(connectionPool);
    }

    @Bean
    @ConditionalOnMissingBean
    public KdbPoolMetrics kdbPoolMetrics(
            KdbConnectionPool pool) {

        return new KdbPoolMetrics(pool);
    }

    @Bean
    public KdbMetricsController kdbMetricsController(
            KdbConnectionPool pool,
            KdbTemplate template) {

        return new KdbMetricsController(pool, template);
    }
}