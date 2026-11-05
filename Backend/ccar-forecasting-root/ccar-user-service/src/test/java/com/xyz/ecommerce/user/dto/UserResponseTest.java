package com.xyz.ecommerce.user.dto;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserResponseTest {

    @Test
    void constructor_setsAllFields() {
        LocalDateTime now = LocalDateTime.now();
        UserResponse response = new UserResponse(1L, "Jane Doe", "jane@example.com", "123 Main St", "555-1234", now);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Jane Doe");
        assertThat(response.getEmail()).isEqualTo("jane@example.com");
        assertThat(response.getAddress()).isEqualTo("123 Main St");
        assertThat(response.getPhone()).isEqualTo("555-1234");
        assertThat(response.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void noArgConstructor_createsEmptyResponse() {
        UserResponse response = new UserResponse();

        assertThat(response.getId()).isNull();
        assertThat(response.getName()).isNull();
        assertThat(response.getEmail()).isNull();
        assertThat(response.getAddress()).isNull();
        assertThat(response.getPhone()).isNull();
        assertThat(response.getCreatedAt()).isNull();
    }

    @Test
    void setters_updateFields() {
        UserResponse response = new UserResponse();
        LocalDateTime now = LocalDateTime.now();

        response.setId(1L);
        response.setName("Jane Doe");
        response.setEmail("jane@example.com");
        response.setAddress("123 Main St");
        response.setPhone("555-1234");
        response.setCreatedAt(now);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Jane Doe");
        assertThat(response.getEmail()).isEqualTo("jane@example.com");
        assertThat(response.getAddress()).isEqualTo("123 Main St");
        assertThat(response.getPhone()).isEqualTo("555-1234");
        assertThat(response.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void constructor_withNullProfileFields() {
        LocalDateTime now = LocalDateTime.now();
        UserResponse response = new UserResponse(1L, "Jane Doe", "jane@example.com", null, null, now);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Jane Doe");
        assertThat(response.getEmail()).isEqualTo("jane@example.com");
        assertThat(response.getAddress()).isNull();
        assertThat(response.getPhone()).isNull();
        assertThat(response.getCreatedAt()).isEqualTo(now);
    }
}
