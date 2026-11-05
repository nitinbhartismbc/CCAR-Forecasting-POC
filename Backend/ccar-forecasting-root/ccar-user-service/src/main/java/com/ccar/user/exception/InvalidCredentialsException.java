package com.ccar.user.exception;

/**
 * Thrown for both "no user with that email" and "password didn't match" -
 * deliberately not distinguished, so the API response can't be used to
 * enumerate which emails are registered.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
