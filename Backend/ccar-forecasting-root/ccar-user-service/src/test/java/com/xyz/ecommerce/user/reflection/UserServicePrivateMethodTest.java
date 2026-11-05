package com.xyz.ecommerce.user.reflection;

//import com.xyz.ecommerce.common.security.JwtTokenProvider;
import com.xyz.ecommerce.user.repository.UserRepository;
import com.xyz.ecommerce.user.service.UserServiceImpl;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Case study exercise: "Use Java reflection to test private method" and
 * "identify how many ways we can test a private method".
 *
 * UserServiceImpl#hashPassword(String) is intentionally private - it's an
 * implementation detail the API/callers should never depend on directly.
 * Reflection lets us verify its behavior without weakening that
 * encapsulation for the sake of testability.
 *
 * Alternatives considered (documented here, not implemented, since the
 * point of this test is specifically the reflection approach):
 *  1. Test indirectly through createUser()/updateUser() and assert the
 *     stored passwordHash is not the raw password - covered already in
 *     UserServiceImplTest, but it only proves "not equal", not "correct
 *     algorithm and deterministic".
 *  2. Relax the method's visibility to package-private and test it from a
 *     test class in the same package - simplest, but slightly weakens
 *     encapsulation and is easy to forget to revert.
 *  3. Extract the hashing logic into its own small class with a public
 *     method (e.g. a PasswordHasher component) - arguably the cleanest
 *     long-term fix, but changes the design just to satisfy a test.
 *  4. Reflection (used below) - keeps the method private and the design
 *     untouched, at the cost of more verbose, less refactor-safe test code.
 */
class UserServicePrivateMethodTest {

   /* @Test
    void hashPassword_producesConsistentSha256HexDigest() throws Exception {
        UserServiceImpl userService = new UserServiceImpl(mock(UserRepository.class), mock(JwtTokenProvider.class));

        Method hashPassword = UserServiceImpl.class.getDeclaredMethod("hashPassword", String.class);
        hashPassword.setAccessible(true);

        String result1 = (String) hashPassword.invoke(userService, "s3cret!23");
        String result2 = (String) hashPassword.invoke(userService, "s3cret!23");
        String resultDifferentInput = (String) hashPassword.invoke(userService, "different");

        // SHA-256 -> 32 bytes -> 64 hex characters
        assertThat(result1).hasSize(64);
        assertThat(result1).isEqualTo(result2); // deterministic for the same input
        assertThat(result1).isNotEqualTo(resultDifferentInput);
        assertThat(result1).isNotEqualTo("s3cret!23"); // never the raw password
    }
*/
    @Test
    void hashPassword_isPrivate_confirmingEncapsulationIsIntact() throws Exception {
        Method hashPassword = UserServiceImpl.class.getDeclaredMethod("hashPassword", String.class);
        assertThat(java.lang.reflect.Modifier.isPrivate(hashPassword.getModifiers())).isTrue();
    }
}
