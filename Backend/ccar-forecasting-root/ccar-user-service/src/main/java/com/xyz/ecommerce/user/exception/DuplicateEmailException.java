package com.xyz.ecommerce.user.exception;

public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException(String email) {
        super("A user with email " + email + " already exists");
    }
}
