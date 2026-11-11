package com.ccar.common.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemDetailFactoryTest {

    @Test
    void createsSharedFormat() {
        ProblemDetail p = ProblemDetailFactory.create(ErrorCode.USER_NOT_FOUND, "User 7 not found",
                "/users/7", "corr-1", "user-service");

        assertThat(p.getStatus()).isEqualTo(404);
        assertThat(p.getType()).hasToString("urn:ccar:error:user-not-found");
        assertThat(p.getTitle()).isEqualTo("User not found");
        assertThat(p.getDetail()).isEqualTo("User 7 not found");
        assertThat(p.getInstance()).hasToString("/users/7");
        assertThat(p.getProperties())
                .containsEntry("errorCode", "USER_NOT_FOUND")
                .containsEntry("correlationId", "corr-1")
                .containsEntry("service", "user-service")
                .containsKey("timestamp");
    }

    @Test
    void defaultsDetailToTitleAndSanitisesInstance() {
        ProblemDetail p = ProblemDetailFactory.create(ErrorCode.INTERNAL_ERROR, null, "/a b", null, "svc");
        assertThat(p.getDetail()).isEqualTo("Internal server error");
        assertThat(p.getInstance()).hasToString("/a_b");
    }

    @Test
    void everyCodeHasAnErrorStatusAndUniqueType() {
        assertThat(ErrorCode.values()).allSatisfy(c -> assertThat(c.status()).isBetween(400, 599));
        assertThat(Arrays.stream(ErrorCode.values()).map(ErrorCode::type).distinct().count())
                .isEqualTo(ErrorCode.values().length);
    }

    @Test
    void mapsBareStatuses() {
        assertThat(ErrorCode.fromStatus(404)).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(ErrorCode.fromStatus(418)).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ErrorCode.fromStatus(504)).isEqualTo(ErrorCode.DOWNSTREAM_TIMEOUT);
        assertThat(ErrorCode.fromStatus(500)).isEqualTo(ErrorCode.INTERNAL_ERROR);
    }
}
