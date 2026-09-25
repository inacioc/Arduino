package com.example.ordermanagement.infrastructure.adapter.eventprocess;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The listener itself does nothing but log — whether Spring Modulith actually delivers
 * an arbitrary {@code Object}-typed event to it, and persists a completion row for it,
 * needs a live application context and database to observe, so that part belongs in an
 * *IT suite, not here; this just guards the method itself against throwing for whatever
 * payload it's handed.
 */
class ApplicationEventLoggerTest {

    private final ApplicationEventLogger listener = new ApplicationEventLogger();

    @Test
    void doesNotThrowForAnyEventType() {
        assertThatCode(() -> listener.on("a plain string event")).doesNotThrowAnyException();
        assertThatCode(() -> listener.on(42)).doesNotThrowAnyException();
        assertThatCode(() -> listener.on(new Object())).doesNotThrowAnyException();
    }
}
