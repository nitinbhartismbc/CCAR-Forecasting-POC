package com.xyz.ecommerce.user.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    @Test
    void builder_constructsUserWithAllFields() {
        LocalDateTime now = LocalDateTime.now();
        UserProfile profile = new UserProfile("123 Main St", "555-1234", "dark-mode");
        
        User user = User.builder()
                .name("John Doe")
                .email("john@example.com")
                .passwordHash("hashedpass")
                .createdAt(now)
                .profile(profile)
                .build();

        assertThat(user.getName()).isEqualTo("John Doe");
        assertThat(user.getEmail()).isEqualTo("john@example.com");
        assertThat(user.getPasswordHash()).isEqualTo("hashedpass");
        assertThat(user.getCreatedAt()).isEqualTo(now);
        assertThat(user.getProfile()).isEqualTo(profile);
    }

    @Test
    void builder_setsDefaultCreatedAt_whenNull() {
        User user = User.builder()
                .name("Jane Doe")
                .email("jane@example.com")
                .passwordHash("hash")
                .build();

        assertThat(user.getCreatedAt()).isNotNull();
    }

    @Test
    void builder_throwsException_whenNameBlank() {
        assertThatThrownBy(() -> User.builder()
                .name("")
                .email("test@example.com")
                .passwordHash("hash")
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("name is required");
    }

    @Test
    void builder_throwsException_whenNameNull() {
        assertThatThrownBy(() -> User.builder()
                .name(null)
                .email("test@example.com")
                .passwordHash("hash")
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("name is required");
    }

    @Test
    void builder_throwsException_whenEmailBlank() {
        assertThatThrownBy(() -> User.builder()
                .name("Jane Doe")
                .email("")
                .passwordHash("hash")
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("email is required");
    }

    @Test
    void builder_throwsException_whenEmailNull() {
        assertThatThrownBy(() -> User.builder()
                .name("Jane Doe")
                .email(null)
                .passwordHash("hash")
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("email is required");
    }

    @Test
    void setters_updateFields() {
        User user = User.builder()
                .name("Original")
                .email("original@example.com")
                .passwordHash("hash")
                .build();

        user.setName("Updated");
        user.setEmail("updated@example.com");
        user.setPasswordHash("newhash");

        assertThat(user.getName()).isEqualTo("Updated");
        assertThat(user.getEmail()).isEqualTo("updated@example.com");
        assertThat(user.getPasswordHash()).isEqualTo("newhash");
    }

    @Test
    void setProfile_setsProfileBidirectionally() {
        User user = User.builder()
                .name("Jane Doe")
                .email("jane@example.com")
                .passwordHash("hash")
                .build();
        
        UserProfile profile = new UserProfile("123 Main St", "555-1234", "dark-mode");
        user.setProfile(profile);

        assertThat(user.getProfile()).isEqualTo(profile);
        assertThat(profile.getUser()).isEqualTo(user);
    }

    @Test
    void setProfile_withNull_allowsNullProfile() {
        User user = User.builder()
                .name("Jane Doe")
                .email("jane@example.com")
                .passwordHash("hash")
                .build();
        
        user.setProfile(null);

        assertThat(user.getProfile()).isNull();
    }
}
