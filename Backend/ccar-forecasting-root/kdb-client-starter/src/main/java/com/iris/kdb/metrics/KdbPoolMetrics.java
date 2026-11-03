package com.iris.kdb.metrics;

import com.iris.kdb.pool.KdbConnectionPool;
import com.kx.c;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
public class KdbPoolMetrics {

    private final KdbConnectionPool pool;

    public KdbPoolMetrics(KdbConnectionPool pool) {
        this.pool = pool;
    }

//    @Scheduled(fixedDelay = 10000)
    public void logPoolStats() {

        GenericObjectPool<c> connectionPool =
                pool.getPool();

        log.info(
                "KDB Pool Stats => Active={}, Idle={}, Waiting={}, MaxTotal={}",
                connectionPool.getNumActive(),
                connectionPool.getNumIdle(),
                connectionPool.getNumWaiters(),
                connectionPool.getMaxTotal()
        );
    }
}