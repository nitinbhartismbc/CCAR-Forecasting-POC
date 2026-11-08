package com.ccar.user.service;

import com.ccar.user.dto.LoginRequest;
import com.ccar.user.dto.LoginResponse;
import com.ccar.user.dto.UserRequest;
import com.ccar.user.dto.UserResponse;

import java.util.List;

public interface UserService {

    UserResponse createUser(UserRequest request);

    UserResponse getUserById(Long id);

    List<UserResponse> getAllUsers();

    UserResponse updateUser(Long id, UserRequest request);

    void deleteUser(Long id);

    LoginResponse login(LoginRequest request);
}
