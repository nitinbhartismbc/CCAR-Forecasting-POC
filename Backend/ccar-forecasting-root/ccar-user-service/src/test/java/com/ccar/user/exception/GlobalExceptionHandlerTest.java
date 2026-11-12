package com.ccar.user.exception;

import com.ccar.user.controller.UserController;
import com.ccar.user.dto.UserRequest;
import com.ccar.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * One test per handler mapping of user-service. Every error - application, framework or unexpected - must come
 * back as application/problem+json in the shared format, with a body status equal to the HTTP status.
 */
@WebMvcTest(UserController.class)
class GlobalExceptionHandlerTest {

    private static final String CID = "user-test-cid-1";
    private static final String VALID_USER =
            "{\"name\":\"Jane\",\"email\":\"jane@example.com\",\"password\":\"s3cret!23\"}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private UserService userService;

    @Test
    void userNotFound_is404() throws Exception {
        when(userService.getUserById(42L)).thenThrow(new UserNotFoundException(42L));

        mvc.perform(get("/users/42").header("X-Correlation-Id", CID))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("X-Correlation-Id", CID))
                .andExpect(jsonPath("$.type").value("urn:ccar:error:user-not-found"))
                .andExpect(jsonPath("$.title").value("User not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("User not found with id: 42"))
                .andExpect(jsonPath("$.instance").value("/users/42"))
                .andExpect(jsonPath("$.errorCode").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.userId").value(42))
                .andExpect(jsonPath("$.correlationId").value(CID))
                .andExpect(jsonPath("$.service").value("user-service"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void duplicateEmail_is409() throws Exception {
        when(userService.createUser(any(UserRequest.class)))
                .thenThrow(new DuplicateEmailException("jane@example.com"));

        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content(VALID_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_EMAIL"));
    }

    /** Regression: the old handler returned HTTP 401 with a body saying 404 / "Not Found". */
    @Test
    void invalidCredentials_is401AndBodyAgrees() throws Exception {
        when(userService.login(any())).thenThrow(new InvalidCredentialsException());

        mvc.perform(post("/users/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jane@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Invalid credentials"))
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void validation_is400WithEveryFieldErrorAndNoRejectedValues() throws Exception {
        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"not-an-email\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors", hasSize(3)))
                .andExpect(jsonPath("$.errors[?(@.field == 'email')].message").value("email must be valid"))
                .andExpect(content().string(not(containsString("not-an-email"))));
    }

    @Test
    void shortPassword_is400WithLengthMessage() throws Exception {
        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Jane\",\"email\":\"jane@example.com\",\"password\":\"Short1!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("password"))
                .andExpect(jsonPath("$.errors[0].message").value("password must be at least 8 characters"));
    }

    @Test
    void eightCharacterPassword_isAccepted() throws Exception {
        when(userService.createUser(any(UserRequest.class))).thenReturn(new com.ccar.user.dto.UserResponse(
                1L, "Jane", "jane@example.com", null, null, java.time.LocalDateTime.now()));

        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Jane\",\"email\":\"jane@example.com\",\"password\":\"Exactly8\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void malformedJson_is400MalformedRequest() throws Exception {
        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content("{\"name\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"));
    }

    @Test
    void nonNumericId_is400InvalidParameter() throws Exception {
        mvc.perform(get("/users/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"));
    }

    @Test
    void unsupportedMethod_is405() throws Exception {
        mvc.perform(patch("/users/1").contentType(MediaType.APPLICATION_JSON).content(VALID_USER))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void unsupportedMediaType_is415() throws Exception {
        mvc.perform(post("/users").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void unknownPath_is404ResourceNotFound() throws Exception {
        mvc.perform(get("/users/1/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void dataIntegrityViolation_is409WithoutSql() throws Exception {
        when(userService.updateUser(eq(1L), any())).thenThrow(new DataIntegrityViolationException(
                "could not execute statement; SQL [update users set email=?]; constraint [UK_EMAIL]"));

        mvc.perform(put("/users/1").contentType(MediaType.APPLICATION_JSON).content(VALID_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONFLICT"))
                .andExpect(jsonPath("$.detail").value(not(containsString("SQL"))));
    }

    @Test
    void databaseDown_is503() throws Exception {
        when(userService.getAllUsers()).thenThrow(new DataAccessResourceFailureException("Connection refused"));

        mvc.perform(get("/users"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.detail").value(not(containsString("refused"))));
    }

    @Test
    void unexpectedException_is500GenericAndCorrelated() throws Exception {
        when(userService.getUserById(7L)).thenThrow(new IllegalStateException("internal secret"));

        mvc.perform(get("/users/7"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value(not(containsString("secret"))))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(header().exists("X-Correlation-Id"));
    }
}
