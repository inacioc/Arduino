package com.example.ordermanagement.domain.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Published when a new order is created, for consumers outside the web
 * application's own process (e.g. the batch app's status-confirmation
 * scheduler) that react to it asynchronously rather than through the
 * synchronous {@code OrderEventPort} (IBM MQ) used for the same moment.
 * <p>
 * A plain record — {@link org.springframework.context.ApplicationEventPublisher}
 * accepts arbitrary POJOs as events, so publishing this requires no Spring
 * Modulith dependency here; only listeners that react to it need one.
 */
public record OrderCreatedIntegrationEvent(UUID orderId, String customerId, LocalDateTime occurredAt) {
}
