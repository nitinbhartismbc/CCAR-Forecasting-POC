package com.ccar.user.exception;

import com.ccar.common.error.CcarException;
import com.ccar.common.error.ErrorCode;

/**
 * 401 {@link ErrorCode#INVALID_CREDENTIALS}.
 * <p>
 * Thrown for both "no user with that email" and "password didn't match" -
 * deliberately not distinguished, so the API response can't be used to
 * enumerate which emails are registered.
 */
public class InvalidCredentialsException extends CcarException {

    public InvalidCredentialsException() {
        super(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
    }
}
