package com.ccar.ccar_api_gateway.error;

import com.ccar.common.error.ErrorCode;
import com.ccar.common.reactive.ReactiveErrorClassifier;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class ReactiveErrorClassifierTest {

    @Test
    void circuitOpen() {
        CircuitBreaker cb = CircuitBreaker.ofDefaults("test");
        cb.transitionToOpenState();
        assertThat(ReactiveErrorClassifier.classify(CallNotPermittedException.createCallNotPermittedException(cb)))
                .isEqualTo(ErrorCode.CIRCUIT_OPEN);
    }

    @Test
    void timeouts() {
        assertThat(ReactiveErrorClassifier.classify(new TimeoutException())).isEqualTo(ErrorCode.DOWNSTREAM_TIMEOUT);
        assertThat(ReactiveErrorClassifier.classify(ReadTimeoutException.INSTANCE))
                .isEqualTo(ErrorCode.DOWNSTREAM_TIMEOUT);
        assertThat(ReactiveErrorClassifier.classify(
                new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Response took longer than timeout")))
                .isEqualTo(ErrorCode.DOWNSTREAM_TIMEOUT);
    }

    @Test
    void downstreamUnavailable() {
        assertThat(ReactiveErrorClassifier.classify(new ConnectException("Connection refused")))
                .isEqualTo(ErrorCode.DOWNSTREAM_UNAVAILABLE);
        assertThat(ReactiveErrorClassifier.classify(new ConnectTimeoutException("connect timed out")))
                .isEqualTo(ErrorCode.DOWNSTREAM_UNAVAILABLE);
        assertThat(ReactiveErrorClassifier.classify(new RuntimeException(new IOException("Connection reset"))))
                .isEqualTo(ErrorCode.DOWNSTREAM_UNAVAILABLE);
        assertThat(ReactiveErrorClassifier.classify(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)))
                .isEqualTo(ErrorCode.DOWNSTREAM_UNAVAILABLE);
    }

    @Test
    void noRouteAndUnknown() {
        assertThat(ReactiveErrorClassifier.classify(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .isEqualTo(ErrorCode.ROUTE_NOT_FOUND);
        assertThat(ReactiveErrorClassifier.classify(new IllegalStateException("boom")))
                .isEqualTo(ErrorCode.INTERNAL_ERROR);
        assertThat(ReactiveErrorClassifier.detail(ErrorCode.INTERNAL_ERROR)).doesNotContain("boom");
    }
}
