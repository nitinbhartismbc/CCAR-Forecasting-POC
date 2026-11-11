package com.ccar.common.tracing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdTest {

    @Test
    void keepsValidIncomingId() {
        assertThat(CorrelationId.resolve("ui-1234_abc.def:9")).isEqualTo("ui-1234_abc.def:9");
    }

    @Test
    void replacesMissingOrUnsafeIds() {
        assertThat(CorrelationId.resolve(null)).hasSize(36);
        assertThat(CorrelationId.resolve("")).hasSize(36);
        // CR/LF would allow forging log lines
        assertThat(CorrelationId.resolve("abc\r\nFAKE LOG LINE")).doesNotContain("FAKE");
        assertThat(CorrelationId.resolve("x".repeat(129))).hasSize(36);
    }
}
