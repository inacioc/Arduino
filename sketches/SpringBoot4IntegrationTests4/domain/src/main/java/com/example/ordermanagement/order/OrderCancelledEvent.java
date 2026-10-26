package com.example.ordermanagement.order;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Published when an order is cancelled, for reactions within the same application
 * process — see {@link com.example.ordermanagement.order.internal.port.out.OrderStatusEventPort}.
 */
public record OrderCancelledEvent(UUID orderId, UUID customerId, LocalDateTime occurredAt) {
}
