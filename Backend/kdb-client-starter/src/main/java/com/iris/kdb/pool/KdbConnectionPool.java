package com.iris.kdb.pool;

import com.iris.kdb.config.KdbProperties;

import com.iris.kdb.metrics.KdbPoolStats;
import kx.c;

import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class KdbConnectionPool {

    private static final Logger log =
            LoggerFactory.getLogger(KdbConnectionPool.class);

    private final GenericObjectPool<c> pool;
    private final KdbProperties properties;

    public KdbConnectionPool(KdbProperties properties) {

        this.properties = properties;
        GenericObjectPoolConfig<c> config =
                new GenericObjectPoolConfig<>();

        config.setMaxTotal(properties.getMaxTotal());
        config.setMaxIdle(properties.getMaxIdle());
        config.setMinIdle(properties.getMinIdle());

        config.setMaxWaitMillis(
                properties.getMaxWaitMillis());

        config.setTestOnBorrow(
                properties.isTestOnBorrow());

        config.setTestWhileIdle(
                properties.isTestWhileIdle());

        config.setTimeBetweenEvictionRunsMillis(
                properties.getTimeBetweenEvictionRunsMillis());

        config.setMinEvictableIdleTimeMillis(
                properties.getMinEvictableIdleTimeMillis());

        config.setBlockWhenExhausted(true);

        pool = new GenericObjectPool<>(
                new KdbConnectionFactory(properties),
                config);

        log.info(
                "KDB Connection Pool initialized [maxTotal={}, maxIdle={}, minIdle={}]",
                config.getMaxTotal(),
                config.getMaxIdle(),
                config.getMinIdle());
    }

    /**
     * Borrow connection from pool
     */
    public c borrow() throws Exception {

        c connection = pool.borrowObject();

        log.debug(
                "Borrowed KDB connection. Active={}, Idle={}",
                pool.getNumActive(),
                pool.getNumIdle());

        return connection;
    }

    /**
     * Return connection to pool
     */
    public void release(c connection) {

        if (connection == null) {
            return;
        }

        pool.returnObject(connection);

        log.debug(
                "Returned KDB connection. Active={}, Idle={}",
                pool.getNumActive(),
                pool.getNumIdle());
    }

    /**
     * Invalidate broken connection
     */
    public void invalidate(c connection) {

        if (connection == null) {
            return;
        }

        try {
            pool.invalidateObject(connection);

            log.warn(
                    "Invalidated broken KDB connection");

        } catch (Exception ex) {

            log.error(
                    "Failed to invalidate connection",
                    ex);
        }
    }

    /**
     * Pool stats
     */
    public int getActiveConnections() {
        return pool.getNumActive();
    }

    public int getIdleConnections() {
        return pool.getNumIdle();
    }

    public int getWaitingThreads() {
        return pool.getNumWaiters();
    }

    public int getMaxPoolSize() {
        return pool.getMaxTotal();
    }

    public GenericObjectPool<c> getPool() {
        return pool;
    }

    public String getHost() {
        return properties.getHost();
    }
    /**
     * Shutdown pool
     */
    public void shutdown() {

        log.info("Shutting down KDB connection pool");

        pool.close();
    }

    public KdbPoolStats getPoolStats() {

        return new KdbPoolStats(
                pool.getNumActive(),
                pool.getNumIdle(),
                pool.getNumWaiters(),
                pool.getMaxTotal());
    }
}