package com.ccar.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class UserRequest {

    /** Minimum password length for registration and password changes (the UI enforces the same rule). */
    public static final int PASSWORD_MIN_LENGTH = 8;

    @NotBlank(message = "name is required")
    private String name;

    @NotBlank(message = "email is required")
    @Email(message = "email must be valid")
    private String email;

    // @NotNull + @Size rather than @NotBlank + @Size, so an empty password reports one error, not two.
    // Login (LoginRequest) deliberately has no length rule, so existing shorter passwords can still sign in.
    @NotNull(message = "password is required")
    @Size(min = PASSWORD_MIN_LENGTH, message = "password must be at least " + PASSWORD_MIN_LENGTH + " characters")
    private String password;

    private String address;

    private String phone;

    private String preferences;

    public UserRequest() {
    }

    public UserRequest(String name, String email, String password, String address, String phone, String preferences) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.address = address;
        this.phone = phone;
        this.preferences = preferences;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPreferences() {
        return preferences;
    }

    public void setPreferences(String preferences) {
        this.preferences = preferences;
    }
}
