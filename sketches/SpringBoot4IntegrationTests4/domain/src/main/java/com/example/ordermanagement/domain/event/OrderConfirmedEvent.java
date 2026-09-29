package com.example.ordermanagement.domain.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Published when a PENDING order is confirmed, for reactions within the same
 * application process (e.g. an audit trail) — see
 * {@link com.example.ordermanagement.domain.port.out.OrderStatusEventPort}.
 * <p>
 * A plain record with no Spring/Modulith import of its own: the publishing mechanism is
 * an adapter concern, not this type's. Kept separate from {@code OrderCompletedEvent} and
 * {@code OrderCancelledEvent} (rather than one generic "status changed" event carrying an
 * enum) so a listener can subscribe to exactly the transition it cares about by type,
 * which is also what lets Spring Modulith's module-dependency analysis see which event
 * types actually cross a module boundary.
 */
public record OrderConfirmedEvent(UUID orderId, UUID customerId, LocalDateTime occurredAt) {
}
