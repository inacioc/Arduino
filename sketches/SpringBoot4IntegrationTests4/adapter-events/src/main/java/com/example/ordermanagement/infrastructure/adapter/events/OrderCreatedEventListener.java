package com.example.ordermanagement.infrastructure.adapter.events;

import com.example.ordermanagement.domain.event.OrderCreatedIntegrationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Deliberately does nothing beyond logging.
 * <p>
 * Spring Modulith only persists a row to its standard {@code event_publication} table
 * for an event type that has at least one {@code @ApplicationModuleListener} registered
 * <em>in this same process</em> (the web app). This listener exists purely to cause that
 * persistence — adapter-out-batch's scheduled poller, running as a separate process, is
 * the actual consumer: it reads incomplete {@code OrderCreatedIntegrationEvent}
 * publications straight out of that shared table (via {@code EventPublicationRegistry},
 * pointed at the same Postgres database) and marks them completed itself once it has
 * written them to a file. See {@code OrderConfirmationRequestPoller} in adapter-out-batch.
 */
@Component
public class OrderCreatedEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedEventListener.class);

    @ApplicationModuleListener
    void on(OrderCreatedIntegrationEvent event) {
        log.debug("Order {} queued for confirmation via the batch scheduler", event.orderId());
    }
}
