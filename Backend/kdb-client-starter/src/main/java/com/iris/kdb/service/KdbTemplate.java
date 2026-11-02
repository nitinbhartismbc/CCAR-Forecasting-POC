package com.iris.kdb.service;

import com.iris.kdb.exception.KdbConnectionException;
import com.iris.kdb.exception.KdbPoolExhaustedException;
import com.iris.kdb.exception.KdbQueryException;
import com.iris.kdb.pool.KdbConnectionPool;

import kx.c;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.NoSuchElementException;

@RequiredArgsConstructor
public class KdbTemplate {

    private static final Logger log =
            LoggerFactory.getLogger(KdbTemplate.class);

    private final KdbConnectionPool pool;

    /**
     * Generic execute
     */
    @SuppressWarnings("unchecked")
    public <T> T execute(String query) {

        c connection = null;

        try {

            connection = pool.borrow();

            log.debug("Executing KDB query : {}", query);

            return (T) connection.k(query);

        } catch (NoSuchElementException ex) {

            throw new KdbPoolExhaustedException(
                    "KDB connection pool exhausted",
                    ex);

        } catch (Exception ex) {

            if (connection != null) {
                pool.invalidate(connection);
                connection = null;
            }

            throw new KdbQueryException(
                    "Failed to execute query : " + query,
                    ex);

        } finally {

            if (connection != null) {
                pool.release(connection);
            }
        }
    }

    /**
     * Type-safe execute
     */
    public <T> T execute(
            String query,
            Class<T> clazz) {

        c connection = null;

        try {

            connection = pool.borrow();

            Object result = connection.k(query);

            return clazz.cast(result);

        } catch (NoSuchElementException ex) {

            throw new KdbPoolExhaustedException(
                    "KDB connection pool exhausted",
                    ex);

        } catch (ClassCastException ex) {

            throw new KdbQueryException(
                    "Unable to cast KDB result to "
                            + clazz.getName(),
                    ex);

        } catch (Exception ex) {

            if (connection != null) {
                pool.invalidate(connection);
                connection = null;
            }

            throw new KdbConnectionException(
                    "KDB query execution failed",
                    ex);

        } finally {

            if (connection != null) {
                pool.release(connection);
            }
        }
    }

    /**
     * Async query (fire and forget)
     */
    public void executeAsync(String query) {

        new Thread(() -> {
            try {
                execute(query);
            } catch (Exception ex) {
                log.error(
                        "Async query failed : {}",
                        query,
                        ex);
            }
        }).start();
    }

    /**
     * Connection health check
     */
    public boolean ping() {

        c connection = null;

        try {

            connection = pool.borrow();

            Object result =
                    connection.k(".z.p");

            return result != null;

        } catch (Exception ex) {

            log.error(
                    "KDB ping failed",
                    ex);

            return false;

        } finally {

            if (connection != null) {
                pool.release(connection);
            }
        }
    }

    /**
     * Pool stats
     */
    public void printPoolStats() {

        log.info(
                "KDB Pool Stats => Active={}, Idle={}, Waiting={}, Max={}",
                pool.getActiveConnections(),
                pool.getIdleConnections(),
                pool.getWaitingThreads(),
                pool.getMaxPoolSize()
        );
    }
}