package com.example.ordermanagement.infrastructure.adapter.events;

import com.example.ordermanagement.domain.event.OrderCreatedIntegrationEvent;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Verifies this adapter is a pure pass-through to Spring's event publisher — the seam that
 * keeps {@code ApplicationEventPublisher} out of the domain. Whether Spring Modulith then
 * durably persists that publication needs a live database and a registered
 * {@code @ApplicationModuleListener}, so that part is exercised elsewhere (an *IT suite),
 * not here.
 */
class OrderCreatedIntegrationEventPublisherTest {

    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final OrderCreatedIntegrationEventPublisher publisher =
            new OrderCreatedIntegrationEventPublisher(eventPublisher);

    @Test
    void publish_forwardsTheEventUnchanged() {
        OrderCreatedIntegrationEvent event =
                new OrderCreatedIntegrationEvent(UUID.randomUUID(), "cust-1", LocalDateTime.now());

        publisher.publish(event);

        verify(eventPublisher).publishEvent(event);
    }
}
