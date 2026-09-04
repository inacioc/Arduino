package com.example.ordermanagement.infrastructure.adapter.events;

import com.example.ordermanagement.domain.event.OrderCreatedIntegrationEvent;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The listener itself does nothing but log — its real job is causing Spring Modulith to
 * persist a row to {@code event_publication} for {@link OrderCreatedIntegrationEvent},
 * which only happens with a registered {@code @ApplicationModuleListener} in the same
 * process (see the class Javadoc). That registry behavior needs a live database to
 * observe, so it's exercised by the *IT suite, not here; this just guards the method
 * itself against throwing.
 */
class OrderCreatedEventListenerTest {

    private final OrderCreatedEventListener listener = new OrderCreatedEventListener();

    @Test
    void doesNotThrowWhenHandlingTheEvent() {
        var event = new OrderCreatedIntegrationEvent(UUID.randomUUID(), "cust-1", LocalDateTime.now());

        assertThatCode(() -> listener.on(event)).doesNotThrowAnyException();
    }
}
