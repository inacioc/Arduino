package com.example.ordermanagement.infrastructure.adapter.events;

import com.example.ordermanagement.domain.event.OrderCreatedIntegrationEvent;
import com.example.ordermanagement.domain.port.out.OrderIntegrationEventPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Implements {@link OrderIntegrationEventPort} by handing the event straight to Spring's
 * {@link ApplicationEventPublisher} — the domain builds and owns {@link OrderCreatedIntegrationEvent};
 * this adapter is the only place that knows the mechanism behind publishing it is Spring
 * Modulith.
 * <p>
 * Spring Modulith only persists a row to its standard {@code event_publication} table for
 * an event type that has at least one {@code @ApplicationModuleListener} registered in this
 * same process — see {@link OrderCreatedEventListener}, which exists purely to cause that
 * persistence. {@code adapter-out-batch}'s scheduled poller, running as a separate process,
 * is the actual consumer: it reads incomplete publications straight out of that shared table
 * (pointed at the same Postgres database) and marks them completed itself once it has
 * written them to a file. See {@code OrderConfirmationRequestPoller} in adapter-out-batch.
 */
@Component
public class OrderCreatedIntegrationEventPublisher implements OrderIntegrationEventPort {

    private final ApplicationEventPublisher eventPublisher;

    public OrderCreatedIntegrationEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void publish(OrderCreatedIntegrationEvent event) {
        eventPublisher.publishEvent(event);
    }
}
