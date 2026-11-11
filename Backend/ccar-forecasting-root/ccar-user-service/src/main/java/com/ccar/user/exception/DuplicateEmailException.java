package com.ccar.user.exception;

import com.ccar.common.error.CcarException;
import com.ccar.common.error.ErrorCode;

/** 409 {@link ErrorCode#DUPLICATE_EMAIL}. */
public class DuplicateEmailException extends CcarException {

    public DuplicateEmailException(String email) {
        super(ErrorCode.DUPLICATE_EMAIL, "A user with email " + email + " already exists");
    }
}
