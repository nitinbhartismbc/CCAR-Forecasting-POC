package com.ccar.common.web;

import com.ccar.common.error.ErrorCode;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Spring persistence failures (JPA / JDBC), registered only when spring-tx's {@code DataAccessException} is on the
 * classpath. The SQL, constraint names and driver messages are logged, never returned.
 */
public class PersistenceErrorMapper implements CcarErrorMapper {

    @Override
    public MappedError map(Throwable ex) {
        // Unique-constraint race (e.g. two registrations with the same email at once).
        if (ex instanceof DataIntegrityViolationException) {
            return MappedError.of(ErrorCode.CONFLICT, "The request conflicts with existing data.");
        }
        // Database unreachable / other persistence failures.
        if (ex instanceof DataAccessException) {
            return MappedError.of(ErrorCode.SERVICE_UNAVAILABLE, "The data store is currently unavailable.");
        }
        return null;
    }
}
