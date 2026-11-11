package com.ccar.ccar_api_gateway.error;

import com.ccar.common.error.ErrorCode;
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

class GatewayErrorClassifierTest {

    @Test
    void circuitOpen() {
        CircuitBreaker cb = CircuitBreaker.ofDefaults("test");
        cb.transitionToOpenState();
        assertThat(GatewayErrorClassifier.classify(CallNotPermittedException.createCallNotPermittedException(cb)))
                .isEqualTo(ErrorCode.CIRCUIT_OPEN);
    }

    @Test
    void timeouts() {
        assertThat(GatewayErrorClassifier.classify(new TimeoutException())).isEqualTo(ErrorCode.DOWNSTREAM_TIMEOUT);
        assertThat(GatewayErrorClassifier.classify(ReadTimeoutException.INSTANCE))
                .isEqualTo(ErrorCode.DOWNSTREAM_TIMEOUT);
        assertThat(GatewayErrorClassifier.classify(
                new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Response took longer than timeout")))
                .isEqualTo(ErrorCode.DOWNSTREAM_TIMEOUT);
    }

    @Test
    void downstreamUnavailable() {
        assertThat(GatewayErrorClassifier.classify(new ConnectException("Connection refused")))
                .isEqualTo(ErrorCode.DOWNSTREAM_UNAVAILABLE);
        assertThat(GatewayErrorClassifier.classify(new ConnectTimeoutException("connect timed out")))
                .isEqualTo(ErrorCode.DOWNSTREAM_UNAVAILABLE);
        assertThat(GatewayErrorClassifier.classify(new RuntimeException(new IOException("Connection reset"))))
                .isEqualTo(ErrorCode.DOWNSTREAM_UNAVAILABLE);
        assertThat(GatewayErrorClassifier.classify(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)))
                .isEqualTo(ErrorCode.DOWNSTREAM_UNAVAILABLE);
    }

    @Test
    void noRouteAndUnknown() {
        assertThat(GatewayErrorClassifier.classify(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .isEqualTo(ErrorCode.ROUTE_NOT_FOUND);
        assertThat(GatewayErrorClassifier.classify(new IllegalStateException("boom")))
                .isEqualTo(ErrorCode.INTERNAL_ERROR);
        assertThat(GatewayErrorClassifier.detail(ErrorCode.INTERNAL_ERROR)).doesNotContain("boom");
    }
}
