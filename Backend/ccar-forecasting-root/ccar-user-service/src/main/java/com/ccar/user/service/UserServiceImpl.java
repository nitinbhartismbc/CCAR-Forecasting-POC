package com.ccar.user.service;

import com.ccar.user.dto.LoginRequest;
import com.ccar.user.dto.LoginResponse;
import com.ccar.user.dto.UserRequest;
import com.ccar.user.dto.UserResponse;
import com.ccar.user.entity.User;
import com.ccar.user.entity.UserProfile;
import com.ccar.user.exception.DuplicateEmailException;
import com.ccar.user.exception.InvalidCredentialsException;
import com.ccar.user.exception.UserNotFoundException;
import com.ccar.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.ccar.user.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;

/**
 * IoC in action: this bean is constructor-injected with its collaborators by
 * the Spring container (see UserService/UserRepository below) rather than
 * constructing them itself - the DI/IoC pattern called out in the case study.
 */
@Service
public class UserServiceImpl implements UserService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;


    public UserServiceImpl(
            UserRepository userRepository,
            JwtTokenProvider jwtTokenProvider,
            PasswordEncoder passwordEncoder) {

        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserResponse createUser(UserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException(request.getEmail());
        }

        UserProfile profile = null;
        if (hasProfileData(request)) {
            profile = new UserProfile(request.getAddress(), request.getPhone(), request.getPreferences());
        }

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .passwordHash(hashPassword(request.getPassword()))
                .profile(profile)
                .build();

        User saved = userRepository.save(user);
        LOGGER.info("Created user with id {}", saved.getId());
        return toResponse(saved);
    }

    @Override
    public UserResponse getUserById(Long id) {
        User user = findUserOrThrow(id);
        return toResponse(user);
    }

    @Override
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    public UserResponse updateUser(Long id, UserRequest request) {
        User user = findUserOrThrow(id);
        // same rule as createUser: changing to an email another user already has is a 409, not a DB error
        if (!request.getEmail().equalsIgnoreCase(user.getEmail())
                && userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException(request.getEmail());
        }
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPasswordHash(hashPassword(request.getPassword()));
        }
        if (hasProfileData(request)) {
            UserProfile profile = user.getProfile() != null
                    ? user.getProfile()
                    : new UserProfile(null, null, null);
            profile.setAddress(request.getAddress());
            profile.setPhone(request.getPhone());
            profile.setPreferences(request.getPreferences());
            user.setProfile(profile);
        }
        User saved = userRepository.save(user);
        LOGGER.info("Updated user with id {}", saved.getId());
        return toResponse(saved);
    }

    @Override
    public void deleteUser(Long id) {
        User user = findUserOrThrow(id);
        userRepository.delete(user);
        LOGGER.info("Deleted user with id {}", id);
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        LOGGER.info("Login attempt for email: {}", request.getEmail());

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    LOGGER.warn("Login failed: Invalid credentials");
                    return new InvalidCredentialsException();
                });

        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPasswordHash())) {

            LOGGER.warn("Login failed: Invalid credentials");
            throw new InvalidCredentialsException();
        }

        LOGGER.debug("Credentials verified for userId: {}", user.getId());

        String token = jwtTokenProvider.generateToken(user.getEmail());

        LOGGER.info("Login successful for userId: {}", user.getId());

        return new LoginResponse(
                token,
                jwtTokenProvider.getExpirationSeconds(),
                user.getId()
        );
    }

    /* @Override
     public LoginResponse login(LoginRequest request) {
         User user = userRepository.findByEmail(request.getEmail())
                 .orElseThrow(InvalidCredentialsException::new);

         String submittedHash = hashPassword(request.getPassword());
         if (!constantTimeEquals(submittedHash, user.getPasswordHash())) {
             throw new InvalidCredentialsException();
         }

       //  String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail());
         LOGGER.info("User " + user.getId() + " logged in");
         return new LoginResponse(token, jwtTokenProvider.getExpirationSeconds(), user.getId());
     }
 */
    private User findUserOrThrow(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    private boolean hasProfileData(UserRequest request) {
        return request.getAddress() != null || request.getPhone() != null || request.getPreferences() != null;
    }

    /**
     * Private by design: password hashing is an internal implementation
     * detail of the service, not something callers or the API contract
     * should depend on. This is the method exercised via Java reflection
     * in UserServicePrivateMethodTest, per the case study's "test a private
     * method" exercise.
     */
    private String hashPassword(String rawPassword) {
//        try {
//            MessageDigest digest = MessageDigest.getInstance("SHA-256");
//            byte[] hashed = digest.digest(rawPassword.getBytes());
//            return HexFormat.of().formatHex(hashed);
//        } catch (NoSuchAlgorithmException e) {
//            throw new IllegalStateException("SHA-256 algorithm not available", e);
//        }
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * Login-only concern: compares two already-hashed values in constant
     * time, so a timing attack can't be used to guess a stored hash one
     * character at a time. String#equals short-circuits on the first
     * mismatched character, which leaks timing information; MessageDigest's
     * comparison does not.
     */
    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8)
        );
    }

    private UserResponse toResponse(User user) {
        UserProfile profile = user.getProfile();
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                profile != null ? profile.getAddress() : null,
                profile != null ? profile.getPhone() : null,
                user.getCreatedAt()
        );
    }
}
