package com.ccar.user.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "app_user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, optional = true)
    private UserProfile profile;

    protected User() {
        // JPA requires a no-arg constructor
    }

    private User(Builder builder) {
        this.name = builder.name;
        this.email = builder.email;
        this.passwordHash = builder.passwordHash;
        this.createdAt = builder.createdAt != null ? builder.createdAt : LocalDateTime.now();
        this.profile = builder.profile;
        if (this.profile != null) {
            this.profile.setUser(this);
        }
    }

    /**
     * Builder pattern (case study requirement): constructs a User with a
     * readable, safe fluent API rather than a telescoping constructor,
     * since several fields (profile, createdAt) are optional.
     */
    public static class Builder {
        private String name;
        private String email;
        private String passwordHash;
        private LocalDateTime createdAt;
        private UserProfile profile;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder passwordHash(String passwordHash) {
            this.passwordHash = passwordHash;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder profile(UserProfile profile) {
            this.profile = profile;
            return this;
        }

        public User build() {
            if (name == null || name.isBlank()) {
                throw new IllegalStateException("User name is required");
            }
            if (email == null || email.isBlank()) {
                throw new IllegalStateException("User email is required");
            }
            return new User(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public Long getId() {
        return id;
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

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public UserProfile getProfile() {
        return profile;
    }

    public void setProfile(UserProfile profile) {
        this.profile = profile;
        if (profile != null) {
            profile.setUser(this);
        }
    }
}
