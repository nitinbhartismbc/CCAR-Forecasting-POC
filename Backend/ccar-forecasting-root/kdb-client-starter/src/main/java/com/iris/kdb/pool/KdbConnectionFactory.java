package com.iris.kdb.pool;

import com.iris.kdb.config.KdbProperties;
import com.iris.kdb.exception.KdbConnectionException;
import com.kx.c;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class KdbConnectionFactory
        extends BasePooledObjectFactory<c> {

    private static final Logger log =
            LoggerFactory.getLogger(KdbConnectionFactory.class);

    private final KdbProperties properties;

    public KdbConnectionFactory(
            KdbProperties properties) {
        this.properties = properties;
    }

    /**
     * Opens a connection with SO_TIMEOUT = {@code kdb.query-timeout-millis}, so neither the login handshake nor
     * a query can block a thread forever. Failures surface as {@link KdbConnectionException} (never the raw
     * IOException / KException, and never the password).
     */
    @Override
    public c create() {

        String target = properties.getHost() + ":" + properties.getPort();

        log.info("Creating KDB connection {}", target);

        /*
         * Username/password format:
         * "user:password"
         */
        String credentials =
                properties.getUsername()
                        + ":"
                        + properties.getPassword();

        try {
            c connection = new c(
                    properties.getHost(),
                    properties.getPort(),
                    credentials,
                    false,
                    properties.getQueryTimeoutMillis());

            log.info("KDB connection established to {}", target);
            return connection;

        } catch (c.KException ex) {
            // the only KException the constructor throws is "access" (login refused)
            throw new KdbConnectionException(
                    "KDB login refused at " + target + " for user '" + properties.getUsername() + "'", ex);

        } catch (IOException ex) {
            throw new KdbConnectionException(
                    "Cannot connect to KDB at " + target + ": " + ex.getMessage(), ex);
        }
    }

    @Override
    public PooledObject<c> wrap(c connection) {

        return new DefaultPooledObject<>(
                connection);
    }

    @Override
    public void destroyObject(
            PooledObject<c> pooledObject)
            throws Exception {

        c connection = pooledObject.getObject();

        if (connection != null) {

            try {

                connection.close();

                log.info(
                        "KDB connection closed");

            } catch (Exception ex) {

                log.warn(
                        "Error closing KDB connection: {}",
                        ex.toString());
            }
        }

        super.destroyObject(pooledObject);
    }

    @Override
    public boolean validateObject(
            PooledObject<c> pooledObject) {

        c connection = pooledObject.getObject();

        try {

            Object result =
                    connection.k("1+1");

            return result != null;

        } catch (Exception ex) {

            log.warn(
                    "KDB connection validation failed: {}",
                    ex.toString());

            return false;
        }
    }

    @Override
    public void activateObject(
            PooledObject<c> pooledObject)
            throws Exception {

        log.debug(
                "Borrowing KDB connection");

        super.activateObject(pooledObject);
    }

    @Override
    public void passivateObject(
            PooledObject<c> pooledObject)
            throws Exception {

        log.debug(
                "Returning KDB connection");

        super.passivateObject(pooledObject);
    }
}
