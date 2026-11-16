package com.ccar.common.web;

import com.ccar.common.error.ErrorCode;

/**
 * Extension point of the shared {@link GlobalExceptionHandler}: maps an exception type the handler does not know
 * (a data-store or library exception) to the error catalogue. Every {@code CcarErrorMapper} bean is consulted, in
 * {@code @Order}, for the exception and then each of its causes, before the generic 500.
 * <p>
 * ccar-common registers {@link KdbErrorMapper}, {@link PersistenceErrorMapper} and
 * {@link ConstraintViolationErrorMapper} when their libraries are present; a service can add its own as a bean.
 */
@FunctionalInterface
public interface CcarErrorMapper {

    /**
     * @return the mapping, or {@code null} when this mapper does not handle {@code ex}
     */
    MappedError map(Throwable ex);

    /**
     * @param detail            client-facing text: must never contain exception messages, hosts or SQL
     * @param retryAfterSeconds sent as {@code Retry-After}; {@code null} for none
     */
    record MappedError(ErrorCode code, String detail, Long retryAfterSeconds) {

        public static MappedError of(ErrorCode code, String detail) {
            return new MappedError(code, detail, null);
        }
    }
}
