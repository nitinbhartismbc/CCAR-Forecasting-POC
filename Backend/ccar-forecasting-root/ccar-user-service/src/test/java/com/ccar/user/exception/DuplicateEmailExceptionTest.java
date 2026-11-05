package com.ccar.user.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DuplicateEmailExceptionTest {

    @Test
    void constructor_createsMessageWithEmail() {
        DuplicateEmailException exception = new DuplicateEmailException("test@example.com");

        assertThat(exception.getMessage()).contains("test@example.com");
        assertThat(exception.getMessage()).contains("already exists");
    }

    @Test
    void exceptionIsRuntimeException() {
        DuplicateEmailException exception = new DuplicateEmailException("test@example.com");

        assertThat(exception).isInstanceOf(RuntimeException.class);
    }
}
