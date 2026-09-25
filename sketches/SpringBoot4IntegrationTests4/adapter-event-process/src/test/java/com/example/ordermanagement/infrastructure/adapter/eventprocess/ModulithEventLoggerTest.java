package com.example.ordermanagement.infrastructure.adapter.eventprocess;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.modulith.events.core.PublicationTargetIdentifier;
import org.springframework.modulith.events.core.TargetEventPublication;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the logger reads whatever is pending and, crucially, never completes any of
 * it — see {@link ModulithEventLogger}'s javadoc for why that's the whole point (it must
 * stay safe to add alongside a real consumer like {@code OrderConfirmationRequestPoller}
 * without racing it for the same rows).
 */
class ModulithEventLoggerTest {

    private final EventPublicationRegistry registry = mock(EventPublicationRegistry.class);
    private final ModulithEventLogger logger = new ModulithEventLogger(registry);

    @Test
    void logsEveryPendingPublicationRegardlessOfEventType_andNeverCompletesAny() {
        TargetEventPublication orderEvent = TargetEventPublication.of(
                "some-order-event-payload", PublicationTargetIdentifier.of("listener-1"));
        TargetEventPublication otherEvent = TargetEventPublication.of(
                42, PublicationTargetIdentifier.of("listener-2"));
        when(registry.findIncompletePublications()).thenReturn(List.of(orderEvent, otherEvent));

        assertThatCode(logger::logIncompletePublications).doesNotThrowAnyException();

        verify(registry, never()).markCompleted(any(), any());
    }

    @Test
    void doesNothingWhenNoPublicationsArePending() {
        when(registry.findIncompletePublications()).thenReturn(List.of());

        assertThatCode(logger::logIncompletePublications).doesNotThrowAnyException();

        verify(registry, never()).markCompleted(any(), any());
    }
}
