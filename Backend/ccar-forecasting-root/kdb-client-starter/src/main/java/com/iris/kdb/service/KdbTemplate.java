package com.iris.kdb.service;

import com.iris.kdb.exception.KdbConnectionException;
import com.iris.kdb.exception.KdbException;
import com.iris.kdb.exception.KdbPoolExhaustedException;
import com.iris.kdb.exception.KdbQueryException;
import com.iris.kdb.exception.KdbTimeoutException;
import com.iris.kdb.pool.KdbConnectionPool;

import com.kx.c;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Executes q queries on a pooled KDB connection and translates every failure into the
 * {@link KdbException} hierarchy:
 *
 * <table>
 *   <tr><th>failure</th><th>exception</th><th>connection</th></tr>
 *   <tr><td>no connection free within max-wait</td><td>{@link KdbPoolExhaustedException}</td><td>-</td></tr>
 *   <tr><td>cannot connect / login refused</td><td>{@link KdbConnectionException}</td><td>-</td></tr>
 *   <tr><td>q error ({@code c.KException}), wrong result type</td><td>{@link KdbQueryException}</td>
 *       <td>returned to pool (still healthy)</td></tr>
 *   <tr><td>socket read timeout</td><td>{@link KdbTimeoutException}</td><td>invalidated</td></tr>
 *   <tr><td>I/O error</td><td>{@link KdbConnectionException}</td><td>invalidated</td></tr>
 *   <tr><td>anything else</td><td>{@link KdbException}</td><td>invalidated</td></tr>
 * </table>
 *
 * Exception messages never contain the query text (it is logged, truncated, by KdbLoggingAspect).
 */
public class KdbTemplate {

    private static final Logger log =
            LoggerFactory.getLogger(KdbTemplate.class);

    private static final Executor ASYNC_EXECUTOR = task -> {
        Thread thread = new Thread(task, "kdb-async");
        thread.setDaemon(true);
        thread.start();
    };

    private final KdbConnectionPool pool;

    public KdbTemplate(KdbConnectionPool pool) {
        this.pool = pool;
    }

    /**
     * Generic execute
     */
    @SuppressWarnings("unchecked")
    public <T> T execute(String query) {
        return (T) run(query);
    }

    /**
     * Type-safe execute: fails with {@link KdbQueryException} when KDB returns something other than {@code clazz}
     * (including null).
     */
    public <T> T execute(
            String query,
            Class<T> clazz) {

        Object result = run(query);

        if (!clazz.isInstance(result)) {
            throw new KdbQueryException(
                    "Unexpected KDB result type: expected " + clazz.getName() + " but got "
                            + (result == null ? "null" : result.getClass().getName()));
        }
        return clazz.cast(result);
    }

    /**
     * Async query on a background thread. The caller's MDC (correlation id) is carried over, so the async
     * log lines stay traceable; failures complete the future exceptionally and are logged.
     */
    public CompletableFuture<Object> executeAsync(String query) {

        Map<String, String> mdc = MDC.getCopyOfContextMap();

        return CompletableFuture.supplyAsync(() -> {
            if (mdc != null) {
                MDC.setContextMap(mdc);
            }
            try {
                return run(query);
            } catch (KdbException ex) {
                log.error("Async KDB query failed: {}", ex.toString());
                throw ex;
            } finally {
                MDC.clear();
            }
        }, ASYNC_EXECUTOR);
    }

    /**
     * Connection health check
     */
    public boolean ping() {

        try {
            return run(".z.p") != null;
        } catch (KdbException ex) {
            log.warn("KDB ping failed: {}", ex.toString());
            return false;
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

    // -------------------------------------------------------------------------------------------------------

    private Object run(String query) {

        c connection = borrow();
        boolean healthy = true;

        try {
            log.debug("Executing KDB query : {}", query);
            return connection.k(query);

        } catch (c.KException ex) {
            // q-level error ('type, 'length, unknown table...): the connection itself is fine
            throw new KdbQueryException("KDB rejected the query: '" + ex.getMessage(), ex);

        } catch (SocketTimeoutException ex) {
            healthy = false;
            throw new KdbTimeoutException("KDB did not answer within the configured query timeout", ex);

        } catch (IOException ex) {
            healthy = false;
            throw new KdbConnectionException("KDB connection failed during query: " + ex, ex);

        } catch (RuntimeException ex) {
            healthy = false;
            throw new KdbException("Unexpected KDB client failure: " + ex, ex);

        } finally {
            if (healthy) {
                pool.release(connection);
            } else {
                pool.invalidate(connection);
            }
        }
    }

    private c borrow() {

        try {
            return pool.borrow();

        } catch (NoSuchElementException ex) {
            if (isPoolTimeout(ex)) {
                throw new KdbPoolExhaustedException(
                        "No KDB connection became available (pool exhausted): " + ex.getMessage(), ex);
            }
            // e.g. "Unable to validate object": a freshly created connection failed its health check
            throw new KdbConnectionException(
                    "Could not obtain a healthy KDB connection: " + ex.getMessage(), ex);

        } catch (KdbException ex) {
            throw ex; // already translated by KdbConnectionFactory

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new KdbConnectionException("Interrupted while waiting for a KDB connection", ex);

        } catch (Exception ex) {
            throw new KdbConnectionException("Could not obtain a KDB connection: " + ex, ex);
        }
    }

    /** commons-pool2 signals a max-wait timeout / exhausted pool with these NoSuchElementException messages. */
    private static boolean isPoolTimeout(NoSuchElementException ex) {
        String message = ex.getMessage();
        return message != null && (message.startsWith("Timeout waiting") || message.startsWith("Pool exhausted"));
    }
}
