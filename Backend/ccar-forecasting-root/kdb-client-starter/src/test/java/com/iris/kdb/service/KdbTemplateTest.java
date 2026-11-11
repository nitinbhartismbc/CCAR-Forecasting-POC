package com.iris.kdb.service;

import com.iris.kdb.exception.KdbConnectionException;
import com.iris.kdb.exception.KdbException;
import com.iris.kdb.exception.KdbPoolExhaustedException;
import com.iris.kdb.exception.KdbQueryException;
import com.iris.kdb.exception.KdbTimeoutException;
import com.iris.kdb.pool.KdbConnectionPool;
import com.kx.c;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Simulates KDB failures and checks that each is translated to the right exception and that the connection is
 * returned to the pool when it is still healthy, and discarded when it is not.
 */
class KdbTemplateTest {

    private static final String QUERY = "select from forecast_ccar";

    private KdbConnectionPool pool;
    private c connection;
    private KdbTemplate template;

    @BeforeEach
    void setUp() throws Exception {
        pool = mock(KdbConnectionPool.class);
        connection = mock(c.class);
        when(pool.borrow()).thenReturn(connection);
        template = new KdbTemplate(pool);
    }

    @Test
    void success_returnsResultAndReleasesConnection() throws Exception {
        when(connection.k(QUERY)).thenReturn(42L);

        Long result = template.execute(QUERY);

        assertThat(result).isEqualTo(42L);
        verify(pool).release(connection);
        verify(pool, never()).invalidate(connection);
    }

    @Test
    void qError_isQueryException_andConnectionIsKept() throws Exception {
        when(connection.k(QUERY)).thenThrow(kException("forecast_ccar"));

        assertThatThrownBy(() -> template.execute(QUERY))
                .isInstanceOf(KdbQueryException.class)
                .hasMessageContaining("'forecast_ccar")
                .hasMessageNotContaining("select"); // query text never leaks into the message

        verify(pool).release(connection);
        verify(pool, never()).invalidate(connection);
    }

    @Test
    void readTimeout_isTimeoutException_andConnectionIsDiscarded() throws Exception {
        when(connection.k(QUERY)).thenThrow(new SocketTimeoutException("Read timed out"));

        assertThatThrownBy(() -> template.execute(QUERY)).isInstanceOf(KdbTimeoutException.class);

        verify(pool).invalidate(connection);
        verify(pool, never()).release(connection);
    }

    @Test
    void brokenConnection_isConnectionException_andConnectionIsDiscarded() throws Exception {
        when(connection.k(QUERY)).thenThrow(new IOException("Connection reset"));

        assertThatThrownBy(() -> template.execute(QUERY)).isInstanceOf(KdbConnectionException.class);

        verify(pool).invalidate(connection);
        verify(pool, never()).release(connection);
    }

    @Test
    void unexpectedClientFailure_isKdbException_andConnectionIsDiscarded() throws Exception {
        when(connection.k(QUERY)).thenThrow(new ArrayIndexOutOfBoundsException(3));

        assertThatThrownBy(() -> template.execute(QUERY))
                .isExactlyInstanceOf(KdbException.class);

        verify(pool).invalidate(connection);
    }

    @Test
    void poolTimeout_isPoolExhaustedException() throws Exception {
        when(pool.borrow()).thenThrow(
                new NoSuchElementException("Timeout waiting for idle object, borrowMaxWaitDuration=PT5S"));

        assertThatThrownBy(() -> template.execute(QUERY)).isInstanceOf(KdbPoolExhaustedException.class);

        verify(pool, never()).release(connection);
        verify(pool, never()).invalidate(connection);
    }

    @Test
    void newConnectionFailingValidation_isConnectionException_notPoolExhausted() throws Exception {
        when(pool.borrow()).thenThrow(new NoSuchElementException("Unable to validate object"));

        assertThatThrownBy(() -> template.execute(QUERY))
                .isInstanceOf(KdbConnectionException.class)
                .isNotInstanceOf(KdbPoolExhaustedException.class);
    }

    @Test
    void kdbDown_isConnectionException() throws Exception {
        when(pool.borrow()).thenThrow(new ConnectException("Connection refused"));

        assertThatThrownBy(() -> template.execute(QUERY))
                .isInstanceOf(KdbConnectionException.class)
                .hasRootCauseInstanceOf(ConnectException.class);
    }

    @Test
    void factoryException_isPassedThroughUnchanged() throws Exception {
        KdbConnectionException fromFactory = new KdbConnectionException("KDB login refused");
        when(pool.borrow()).thenThrow(fromFactory);

        assertThatThrownBy(() -> template.execute(QUERY)).isSameAs(fromFactory);
    }

    @Test
    void typedExecute_wrongResultType_isQueryException_andConnectionIsKept() throws Exception {
        when(connection.k(QUERY)).thenReturn(42L);

        assertThatThrownBy(() -> template.execute(QUERY, c.Flip.class))
                .isInstanceOf(KdbQueryException.class)
                .hasMessageContaining("c$Flip")
                .hasMessageContaining("java.lang.Long");

        verify(pool).release(connection);
    }

    @Test
    void typedExecute_nullResult_isQueryException() throws Exception {
        when(connection.k(QUERY)).thenReturn(null);

        assertThatThrownBy(() -> template.execute(QUERY, c.Flip.class)).isInstanceOf(KdbQueryException.class);
    }

    @Test
    void ping_returnsFalseInsteadOfThrowing() throws Exception {
        when(connection.k(".z.p")).thenThrow(new IOException("Connection reset"));

        assertThat(template.ping()).isFalse();
        verify(pool).invalidate(connection);
    }

    @Test
    void async_propagatesCorrelationIdAndFailure() throws Exception {
        AtomicReference<String> seenCorrelationId = new AtomicReference<>();
        when(connection.k(QUERY)).thenAnswer(inv -> {
            seenCorrelationId.set(MDC.get("correlationId"));
            throw new IOException("Connection reset");
        });

        MDC.put("correlationId", "corr-async-1");
        CompletableFuture<Object> future;
        try {
            future = template.executeAsync(QUERY);
        } finally {
            MDC.remove("correlationId");
        }

        assertThatThrownBy(() -> future.join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(KdbConnectionException.class);
        future.handle((r, e) -> null).get(5, TimeUnit.SECONDS);
        assertThat(seenCorrelationId.get()).isEqualTo("corr-async-1");
    }

    /** c.KException's constructor is package-private. */
    private static c.KException kException(String message) throws Exception {
        Constructor<c.KException> ctor = c.KException.class.getDeclaredConstructor(String.class);
        ctor.setAccessible(true);
        return ctor.newInstance(message);
    }
}
