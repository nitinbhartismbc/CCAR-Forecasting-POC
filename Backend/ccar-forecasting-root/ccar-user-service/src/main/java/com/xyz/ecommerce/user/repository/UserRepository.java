package com.xyz.ecommerce.user.repository;

import com.xyz.ecommerce.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository / DAO pattern (case study requirement): isolates persistence
 * access behind an interface. Spring Data JPA supplies the implementation
 * at runtime, so the service layer never talks to JDBC/JPA directly.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
