package com.example.ordermanagement.domain.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Published when an order finishes processing and is marked COMPLETED, for reactions
 * within the same application process — see
 * {@link com.example.ordermanagement.domain.port.out.OrderStatusEventPort}.
 * <p>
 * Distinct from {@link com.example.ordermanagement.domain.port.out.OrderEventPort}'s
 * {@code publishOrderCompleted}, which sends the cross-process integration event to IBM MQ
 * for other deployable applications. This one is for in-process side effects only (e.g. an
 * audit trail) and never leaves the JVM that publishes it.
 */
public record OrderCompletedEvent(UUID orderId, String customerId, LocalDateTime occurredAt) {
}
