package com.xyz.ecommerce.user.dto;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserRequestTest {

    @Test
    void constructor_setsAllFields() {
        UserRequest request = new UserRequest("Jane Doe", "jane@example.com", "password123", "123 Main St", "555-1234", "dark-mode");

        assertThat(request.getName()).isEqualTo("Jane Doe");
        assertThat(request.getEmail()).isEqualTo("jane@example.com");
        assertThat(request.getPassword()).isEqualTo("password123");
        assertThat(request.getAddress()).isEqualTo("123 Main St");
        assertThat(request.getPhone()).isEqualTo("555-1234");
        assertThat(request.getPreferences()).isEqualTo("dark-mode");
    }

    @Test
    void noArgConstructor_createsEmptyRequest() {
        UserRequest request = new UserRequest();

        assertThat(request.getName()).isNull();
        assertThat(request.getEmail()).isNull();
        assertThat(request.getPassword()).isNull();
        assertThat(request.getAddress()).isNull();
        assertThat(request.getPhone()).isNull();
        assertThat(request.getPreferences()).isNull();
    }

    @Test
    void setters_updateFields() {
        UserRequest request = new UserRequest();

        request.setName("Jane Doe");
        request.setEmail("jane@example.com");
        request.setPassword("password123");
        request.setAddress("123 Main St");
        request.setPhone("555-1234");
        request.setPreferences("dark-mode");

        assertThat(request.getName()).isEqualTo("Jane Doe");
        assertThat(request.getEmail()).isEqualTo("jane@example.com");
        assertThat(request.getPassword()).isEqualTo("password123");
        assertThat(request.getAddress()).isEqualTo("123 Main St");
        assertThat(request.getPhone()).isEqualTo("555-1234");
        assertThat(request.getPreferences()).isEqualTo("dark-mode");
    }

    @Test
    void constructor_withPartialFields() {
        UserRequest request = new UserRequest("Jane Doe", "jane@example.com", "password123", null, null, null);

        assertThat(request.getName()).isEqualTo("Jane Doe");
        assertThat(request.getEmail()).isEqualTo("jane@example.com");
        assertThat(request.getPassword()).isEqualTo("password123");
        assertThat(request.getAddress()).isNull();
        assertThat(request.getPhone()).isNull();
        assertThat(request.getPreferences()).isNull();
    }
}
