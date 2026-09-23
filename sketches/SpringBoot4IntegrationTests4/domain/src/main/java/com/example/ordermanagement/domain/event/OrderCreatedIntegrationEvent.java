package com.example.ordermanagement.domain.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Published when a new order is created, for consumers outside the web application's own
 * process — e.g. the batch app's status-confirmation scheduler — that react to it
 * asynchronously rather than through the synchronous {@code OrderEventPort} (IBM MQ) used
 * for the same moment.
 * <p>
 * Lives in {@code domain}, not in whichever adapter happens to publish or consume it: it's
 * shared vocabulary any number of applications may need to reference at compile time (to
 * publish it, to read it back off Spring Modulith's {@code event_publication} table, or
 * both), and every one of them already depends on {@code domain}. Keeping it here means
 * none of them has to pull in {@code adapter-events} — the Modulith plumbing itself —
 * just to know this record's shape; only {@link com.example.ordermanagement.domain.port.out.OrderIntegrationEventPort}'s
 * actual implementation needs that. A plain record — {@code ApplicationEventPublisher}
 * accepts arbitrary POJOs as events, so this type needs no Spring Modulith dependency of
 * its own.
 */
public record OrderCreatedIntegrationEvent(UUID orderId, String customerId, LocalDateTime occurredAt) {
}
