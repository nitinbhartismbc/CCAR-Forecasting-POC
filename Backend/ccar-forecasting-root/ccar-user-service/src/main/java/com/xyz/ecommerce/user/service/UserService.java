package com.xyz.ecommerce.user.service;

import com.xyz.ecommerce.user.dto.LoginRequest;
import com.xyz.ecommerce.user.dto.LoginResponse;
import com.xyz.ecommerce.user.dto.UserRequest;
import com.xyz.ecommerce.user.dto.UserResponse;

import java.util.List;

public interface UserService {

    UserResponse createUser(UserRequest request);

    UserResponse getUserById(Long id);

    List<UserResponse> getAllUsers();

    UserResponse updateUser(Long id, UserRequest request);

    void deleteUser(Long id);

   // LoginResponse login(LoginRequest request);
}
