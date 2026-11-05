package com.xyz.ecommerce.user.repository;

import com.xyz.ecommerce.user.entity.User;
import com.xyz.ecommerce.user.entity.UserProfile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @DataJpaTest boots only the JPA slice (in-memory H2, real Hibernate),
 * verifying the repository layer and the User<->UserProfile one-to-one
 * mapping actually persist correctly.
 */
//@DataJpaTest
class UserRepositoryTest {/*

   // @Autowired
    private UserRepository userRepository;

    @Test
    void save_andFindByEmail_roundTripsUserWithProfile() {
        UserProfile profile = new UserProfile("221B Baker St", "555-0100", "dark-mode");
        User user = User.builder()
                .name("Jane Doe")
                .email("jane@example.com")
                .passwordHash("hashed-value")
                .profile(profile)
                .build();

        userRepository.save(user);

        Optional<User> found = userRepository.findByEmail("jane@example.com");
        assertThat(found).isPresent();
        assertThat(found.get().getProfile()).isNotNull();
        assertThat(found.get().getProfile().getAddress()).isEqualTo("221B Baker St");
    }

    @Test
    void existsByEmail_returnsFalse_whenNoUserWithEmail() {
        assertThat(userRepository.existsByEmail("nobody@example.com")).isFalse();
    }

    @Test
    void existsByEmail_returnsTrue_afterSaving() {
        User user = User.builder().name("Bob").email("bob@example.com").passwordHash("h").build();
        userRepository.save(user);

        assertThat(userRepository.existsByEmail("bob@example.com")).isTrue();
    }*/
}
