package com.example.ordermanagement.domain.port.out;

import com.example.ordermanagement.domain.event.OrderCreatedIntegrationEvent;

/**
 * Outbound port for publishing a durable, asynchronous integration event to consumers
 * outside this process — e.g. {@code adapter-out-batch}'s scheduled poller, running as a
 * separate application.
 * <p>
 * Distinct from {@link OrderEventPort}, which publishes the same moments synchronously to
 * IBM MQ: this one exists so the domain never has to know Spring Modulith (or any other
 * durable-event mechanism) is behind it — {@code adapter-events} implements it using
 * {@code ApplicationEventPublisher} and Modulith's event-publication registry, but the
 * domain only ever sees this interface. The domain builds the event itself (it owns
 * {@link OrderCreatedIntegrationEvent}'s shape); the adapter's only job is handing it to
 * whatever mechanism actually publishes it.
 */
public interface OrderIntegrationEventPort {

    void publish(OrderCreatedIntegrationEvent event);
}
