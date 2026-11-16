package com.ccar.common.web;

import com.iris.kdb.exception.KdbConnectionException;
import com.iris.kdb.exception.KdbTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The mapper dispatch of the shared handler; the full MVC path is covered by each service's @WebMvcTest. */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler("test-service",
            List.of(new KdbErrorMapper(30), new PersistenceErrorMapper(), new ConstraintViolationErrorMapper()));

    private final ServletWebRequest request = new ServletWebRequest(new MockHttpServletRequest("GET", "/x"));

    @Test
    void kdbUnavailableIs503WithRetryAfterAndNoInternals() {
        ResponseEntity<Object> response =
                handler.handleUnexpected(new KdbConnectionException("Cannot connect to 10.0.0.1:5000"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("30");
        ProblemDetail body = (ProblemDetail) response.getBody();
        assertThat(body.getProperties()).containsEntry("errorCode", "KDB_UNAVAILABLE")
                .containsEntry("service", "test-service");
        assertThat(body.getDetail()).doesNotContain("10.0.0.1");
    }

    @Test
    void mappedExceptionIsFoundInTheCauseChain() {
        ResponseEntity<Object> response = handler.handleUnexpected(
                new IllegalStateException("wrapper", new KdbTimeoutException("slow")), request);

        assertThat(response.getStatusCode().value()).isEqualTo(504);
        assertThat(response.getHeaders().getFirst("Retry-After")).isNull();
    }

    @Test
    void persistenceFailures() {
        assertThat(handler.handleUnexpected(new DataIntegrityViolationException("uk_email"), request)
                .getStatusCode().value()).isEqualTo(409);
        assertThat(handler.handleUnexpected(new DataAccessResourceFailureException("refused"), request)
                .getStatusCode().value()).isEqualTo(503);
    }

    @Test
    void unmappedIsGeneric500() {
        ResponseEntity<Object> response = handler.handleUnexpected(new NullPointerException("secret"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(((ProblemDetail) response.getBody()).getDetail())
                .isEqualTo(GlobalExceptionHandler.GENERIC_SERVER_ERROR);
    }
}
