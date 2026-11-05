package com.xyz.ecommerce.user.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserProfileTest {

    @Test
    void constructor_setsAllFields() {
        UserProfile profile = new UserProfile("123 Main St", "555-1234", "dark-mode");

        assertThat(profile.getAddress()).isEqualTo("123 Main St");
        assertThat(profile.getPhone()).isEqualTo("555-1234");
        assertThat(profile.getPreferences()).isEqualTo("dark-mode");
    }

    @Test
    void setters_updateFields() {
        UserProfile profile = new UserProfile("Old St", "555-5555", "light-mode");

        profile.setAddress("New St");
        profile.setPhone("555-1111");
        profile.setPreferences("dark-mode");

        assertThat(profile.getAddress()).isEqualTo("New St");
        assertThat(profile.getPhone()).isEqualTo("555-1111");
        assertThat(profile.getPreferences()).isEqualTo("dark-mode");
    }

    @Test
    void setUser_setsUserReference() {
        UserProfile profile = new UserProfile("123 Main St", "555-1234", "dark-mode");
        User user = User.builder()
                .name("Jane Doe")
                .email("jane@example.com")
                .passwordHash("hash")
                .build();

        profile.setUser(user);

        assertThat(profile.getUser()).isEqualTo(user);
    }

    @Test
    void constructor_withNullValues() {
        UserProfile profile = new UserProfile(null, null, null);

        assertThat(profile.getAddress()).isNull();
        assertThat(profile.getPhone()).isNull();
        assertThat(profile.getPreferences()).isNull();
    }
}
