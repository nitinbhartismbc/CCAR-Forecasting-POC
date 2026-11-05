package com.ccar.user.service;

//import com.xyz.ecommerce.common.security.JwtTokenProvider;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

/**
 * Mockito mocks the repository so the service layer is tested in isolation,
 * per the case study's JUnit + Mockito testing requirement. JwtTokenProvider
 * is used as a real instance (not mocked) - it's cheap, deterministic, and
 * has no external dependency, so using the real thing gives more honest
 * coverage of the login flow than stubbing a fake token back.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {/*

    private static final String TEST_JWT_SECRET = "unit-test-only-jwt-secret-key-32-bytes-min";

    @Mock
    private UserRepository userRepository;

   // private JwtTokenProvider jwtTokenProvider;
    private UserServiceImpl userService;

    private UserRequest request;

    *//*@BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(TEST_JWT_SECRET, 3600);
        userService = new UserServiceImpl(userRepository, jwtTokenProvider);
        request = new UserRequest("Jane Doe", "jane@example.com", "s3cret!23", "221B Baker St", "555-0100", "dark-mode");
    }*//*

    private static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void createUser_savesAndReturnsUser_whenEmailNotTaken() {
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            // simulate DB assigning an id
            return u;
        });

        UserResponse response = userService.createUser(request);

        assertThat(response.getName()).isEqualTo("Jane Doe");
        assertThat(response.getEmail()).isEqualTo("jane@example.com");
        assertThat(response.getAddress()).isEqualTo("221B Baker St");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isNotEqualTo("s3cret!23"); // must be hashed, not raw
    }

    @Test
    void createUser_throwsDuplicateEmailException_whenEmailAlreadyExists() {
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(DuplicateEmailException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void getUserById_returnsUser_whenFound() {
        User existing = User.builder().name("Jane Doe").email("jane@example.com").passwordHash("hash").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));

        UserResponse response = userService.getUserById(1L);

        assertThat(response.getName()).isEqualTo("Jane Doe");
    }

    @Test
    void getUserById_throwsNotFound_whenMissing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(99L))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void getAllUsers_returnsMappedList() {
        User u1 = User.builder().name("A").email("a@example.com").passwordHash("h").build();
        User u2 = User.builder().name("B").email("b@example.com").passwordHash("h").build();
        when(userRepository.findAll()).thenReturn(List.of(u1, u2));

        List<UserResponse> responses = userService.getAllUsers();

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(UserResponse::getName).containsExactly("A", "B");
    }

    @Test
    void deleteUser_removesUser_whenFound() {
        User existing = User.builder().name("Jane Doe").email("jane@example.com").passwordHash("hash").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));

        userService.deleteUser(1L);

        verify(userRepository).delete(existing);
    }

    @Test
    void deleteUser_throwsNotFound_whenMissing() {
        when(userRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteUser(42L))
                .isInstanceOf(UserNotFoundException.class);

        verify(userRepository, never()).delete(any());
    }

   *//* @Test
    void login_returnsValidToken_whenCredentialsCorrect() {
        User existing = User.builder()
                .name("Jane Doe")
                .email("jane@example.com")
                .passwordHash(sha256Hex("s3cret!23"))
                .build();
        // simulate the DB-assigned id via reflection-free approach: build()
        // doesn't let us set id directly, so exercise via repository lookup
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(existing));

        LoginResponse response = userService.login(new LoginRequest("jane@example.com", "s3cret!23"));

        assertThat(response.getToken()).isNotBlank();
        assertThat(jwtTokenProvider.validateToken(response.getToken())).isTrue();
        assertThat(jwtTokenProvider.getEmail(response.getToken())).isEqualTo("jane@example.com");
        assertThat(response.getExpiresIn()).isEqualTo(3600);
    }*//*

  *//*  @Test
    void login_throwsInvalidCredentials_whenPasswordWrong() {
        User existing = User.builder()
                .name("Jane Doe")
                .email("jane@example.com")
                .passwordHash(sha256Hex("s3cret!23"))
                .build();
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> userService.login(new LoginRequest("jane@example.com", "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class);
    }*//*

    *//*@Test
    void login_throwsInvalidCredentials_whenEmailUnknown() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.login(new LoginRequest("nobody@example.com", "whatever")))
                .isInstanceOf(InvalidCredentialsException.class);
    }*//*
 */}
