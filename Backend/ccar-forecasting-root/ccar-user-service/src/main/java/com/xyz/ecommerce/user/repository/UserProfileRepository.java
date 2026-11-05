package com.xyz.ecommerce.user.repository;

import com.xyz.ecommerce.user.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {
}
