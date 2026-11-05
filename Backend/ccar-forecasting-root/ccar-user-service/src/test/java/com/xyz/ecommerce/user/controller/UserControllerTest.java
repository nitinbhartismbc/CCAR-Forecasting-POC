package com.xyz.ecommerce.user.controller;

//import com.fasterxml.jackson.databind.ObjectMapper;
import com.xyz.ecommerce.user.dto.UserRequest;
import com.xyz.ecommerce.user.dto.UserResponse;
import com.xyz.ecommerce.user.exception.UserNotFoundException;
import com.xyz.ecommerce.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
//import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

//@WebMvcTest(UserController.class)
class UserControllerTest {
/*
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @Test
    void createUser_returns201AndBody_onValidRequest() throws Exception {
        UserRequest request = new UserRequest("Jane Doe", "jane@example.com", "s3cret!23", null, null, null);
        UserResponse response = new UserResponse(1L, "Jane Doe", "jane@example.com", null, null, LocalDateTime.now());
        when(userService.createUser(any(UserRequest.class))).thenReturn(response);

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Jane Doe"));
    }

    @Test
    void createUser_returns400_whenEmailInvalid() throws Exception {
        UserRequest request = new UserRequest("Jane Doe", "not-an-email", "s3cret!23", null, null, null);

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getUser_returns200_whenFound() throws Exception {
        UserResponse response = new UserResponse(5L, "Jane Doe", "jane@example.com", null, null, LocalDateTime.now());
        when(userService.getUserById(5L)).thenReturn(response);

        mockMvc.perform(get("/users/{id}", 5L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("jane@example.com"));
    }

    @Test
    void getUser_returns404_whenNotFound() throws Exception {
        when(userService.getUserById(999L)).thenThrow(new UserNotFoundException(999L));

        mockMvc.perform(get("/users/{id}", 999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAllUsers_returnsList() throws Exception {
        when(userService.getAllUsers()).thenReturn(List.of(
                new UserResponse(1L, "A", "a@example.com", null, null, LocalDateTime.now()),
                new UserResponse(2L, "B", "b@example.com", null, null, LocalDateTime.now())
        ));

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void deleteUser_returns204() throws Exception {
        mockMvc.perform(delete("/users/{id}", 1L))
                .andExpect(status().isNoContent());
    }*/
}
