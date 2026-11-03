package com.iris.kdb.controller;

import com.iris.kdb.pool.KdbConnectionPool;
import com.iris.kdb.service.KdbTemplate;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/kdb")
public class KdbMetricsController {

    private final KdbConnectionPool pool;
    private final KdbTemplate template;

    public KdbMetricsController(
            KdbConnectionPool pool,
            KdbTemplate template) {

        this.pool = pool;
        this.template = template;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {

        Map<String, Object> response =
                new HashMap<>();

        response.put("connected",
                template.ping());

        response.put("activeConnections",
                pool.getActiveConnections());

        response.put("idleConnections",
                pool.getIdleConnections());

        response.put("waitingThreads",
                pool.getWaitingThreads());

        response.put("maxPoolSize",
                pool.getMaxPoolSize());

        return response;
    }

    @GetMapping("/metrics")
    public Map<String, Object> metrics() {

        Map<String, Object> response =
                new HashMap<>();

        int active = pool.getActiveConnections();
        int max = pool.getMaxPoolSize();

        response.put("host", pool.getHost());
        response.put("activeConnections", active);
        response.put("idleConnections", pool.getIdleConnections());
        response.put("waitingThreads", pool.getWaitingThreads());
        response.put("maxPoolSize", max);
        response.put("utilizationPercent",
                (active * 100.0) / max);

        return response;
    }
}