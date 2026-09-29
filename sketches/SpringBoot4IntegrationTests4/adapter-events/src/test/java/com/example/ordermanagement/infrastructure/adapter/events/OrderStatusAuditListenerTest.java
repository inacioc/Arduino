package com.example.ordermanagement.infrastructure.adapter.events;

import com.example.ordermanagement.domain.event.OrderCancelledEvent;
import com.example.ordermanagement.domain.event.OrderCompletedEvent;
import com.example.ordermanagement.domain.event.OrderConfirmedEvent;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The listener itself only logs - whether Spring Modulith actually delivers each typed
 * event to its matching method and persists/completes the publication row needs a live
 * application context and database to observe (see {@code adapter-in-web}'s
 * {@code OrderStatusModuleEventsIT}); this just guards each method against throwing for a
 * well-formed event.
 */
class OrderStatusAuditListenerTest {

    private final OrderStatusAuditListener listener = new OrderStatusAuditListener();

    @Test
    void onOrderConfirmed_doesNotThrow() {
        assertThatCode(() -> listener.on(new OrderConfirmedEvent(UUID.randomUUID(), UUID.randomUUID(), LocalDateTime.now())))
                .doesNotThrowAnyException();
    }

    @Test
    void onOrderCompleted_doesNotThrow() {
        assertThatCode(() -> listener.on(new OrderCompletedEvent(UUID.randomUUID(), UUID.randomUUID(), LocalDateTime.now())))
                .doesNotThrowAnyException();
    }

    @Test
    void onOrderCancelled_doesNotThrow() {
        assertThatCode(() -> listener.on(new OrderCancelledEvent(UUID.randomUUID(), UUID.randomUUID(), LocalDateTime.now())))
                .doesNotThrowAnyException();
    }
}
