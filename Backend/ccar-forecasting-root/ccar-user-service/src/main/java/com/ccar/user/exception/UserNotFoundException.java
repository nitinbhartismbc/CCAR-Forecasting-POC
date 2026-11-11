package com.ccar.user.exception;

import com.ccar.common.error.CcarException;
import com.ccar.common.error.ErrorCode;

/** 404 {@link ErrorCode#USER_NOT_FOUND}. */
public class UserNotFoundException extends CcarException {

    public UserNotFoundException(Long id) {
        super(ErrorCode.USER_NOT_FOUND, "User not found with id: " + id);
        with("userId", id);
    }

    public UserNotFoundException(String message) {
        super(ErrorCode.USER_NOT_FOUND, message);
    }
}
