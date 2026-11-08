
package com.ccar.user.dto;

public class LoginResponse {

    private String token;
    private long expiresIn; // seconds
    private Long userId;

    public LoginResponse() {
    }

    public LoginResponse(String token, long expiresIn, Long userId) {
        this.token = token;
        this.expiresIn = expiresIn;
        this.userId = userId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
 