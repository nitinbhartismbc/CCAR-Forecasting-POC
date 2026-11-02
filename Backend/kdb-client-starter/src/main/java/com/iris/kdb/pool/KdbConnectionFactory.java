package com.iris.kdb.pool;

import com.iris.kdb.config.KdbProperties;
import kx.c;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KdbConnectionFactory
        extends BasePooledObjectFactory<c> {

    private static final Logger log =
            LoggerFactory.getLogger(KdbConnectionFactory.class);

    private final KdbProperties properties;

    public KdbConnectionFactory(
            KdbProperties properties) {
        this.properties = properties;
    }

    @Override
    public c create() throws Exception {

        log.info(
                "Creating KDB connection {}:{}",
                properties.getHost(),
                properties.getPort());

        /*
         * Username/password format:
         * "user:password"
         */

        String credentials =
                properties.getUsername()
                        + ":"
                        + properties.getPassword();

        c connection = new c(
                properties.getHost(),
                properties.getPort(),
                credentials);

        log.info(
                "KDB connection established");

        return connection;
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

                log.error(
                        "Error closing KDB connection",
                        ex);
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
                    "KDB connection validation failed",
                    ex);

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