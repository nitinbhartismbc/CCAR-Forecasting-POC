package com.xyz.ecommerce.user.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserNotFoundExceptionTest {

    @Test
    void constructorWithId_createsMessageWithId() {
        UserNotFoundException exception = new UserNotFoundException(123L);

        assertThat(exception.getMessage()).contains("123");
        assertThat(exception.getMessage()).contains("not found");
    }

    @Test
    void constructorWithMessage_usesProvidedMessage() {
        UserNotFoundException exception = new UserNotFoundException("Custom error message");

        assertThat(exception.getMessage()).isEqualTo("Custom error message");
    }

    @Test
    void exceptionIsRuntimeException() {
        UserNotFoundException exception = new UserNotFoundException(1L);

        assertThat(exception).isInstanceOf(RuntimeException.class);
    }
}
