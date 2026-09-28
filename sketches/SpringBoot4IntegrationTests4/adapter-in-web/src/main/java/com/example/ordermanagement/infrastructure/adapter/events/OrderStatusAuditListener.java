package com.example.ordermanagement.infrastructure.adapter.events;

import com.example.ordermanagement.domain.event.OrderCancelledEvent;
import com.example.ordermanagement.domain.event.OrderCompletedEvent;
import com.example.ordermanagement.domain.event.OrderConfirmedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Audit trail for order status transitions, reacting to events published by
 * {@link OrderStatusEventPublisher} in this same process.
 * <p>
 * One {@code @ApplicationModuleListener} method per event type, not a single method
 * dispatching on a generic supertype: each gets its own durable
 * {@code event_publication} row, tracked, retried and completed independently, and each
 * is precisely what Spring Modulith's module-dependency analysis sees this module
 * consuming - a broad {@code Object}-typed listener would hide that information.
 * <p>
 * Logging is naturally idempotent, which matters here: {@code @ApplicationModuleListener}
 * gives at-least-once delivery (retried on restart when
 * {@code spring.modulith.events.republish-outstanding-events-on-restart=true}, or if
 * completion bookkeeping fails after a successful run), so a listener with a real side
 * effect (sending an email, calling another service) would need its own idempotency guard;
 * a real business use case beyond logging can reuse this same shape.
 */
@Component
public class OrderStatusAuditListener {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusAuditListener.class);

    @ApplicationModuleListener
    void on(OrderConfirmedEvent event) {
        log.info("Order {} confirmed at {}", event.orderId(), event.occurredAt());
    }

    @ApplicationModuleListener
    void on(OrderCompletedEvent event) {
        log.info("Order {} completed at {}", event.orderId(), event.occurredAt());
    }

    @ApplicationModuleListener
    void on(OrderCancelledEvent event) {
        log.info("Order {} cancelled at {}", event.orderId(), event.occurredAt());
    }
}
