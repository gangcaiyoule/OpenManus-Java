package com.openmanus.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InMemorySessionExecutionGuard Tests")
class InMemorySessionExecutionGuardTest {

    private final InMemorySessionExecutionGuard guard = new InMemorySessionExecutionGuard();

    @Test
    @DisplayName("should allow only one active execution per session")
    void shouldAllowOnlyOneActiveExecutionPerSession() {
        assertThat(guard.tryAcquire("session-1")).isTrue();
        assertThat(guard.tryAcquire("session-1")).isFalse();

        guard.release("session-1");

        assertThat(guard.tryAcquire("session-1")).isTrue();
    }

    @Test
    @DisplayName("should allow different sessions concurrently")
    void shouldAllowDifferentSessionsConcurrently() {
        assertThat(guard.tryAcquire("session-1")).isTrue();
        assertThat(guard.tryAcquire("session-2")).isTrue();
    }
}
