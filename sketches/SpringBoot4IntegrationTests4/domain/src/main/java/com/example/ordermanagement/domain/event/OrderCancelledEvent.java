package com.example.ordermanagement.domain.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Published when an order is cancelled, for reactions within the same application
 * process — see {@link com.example.ordermanagement.domain.port.out.OrderStatusEventPort}.
 */
public record OrderCancelledEvent(UUID orderId, String customerId, LocalDateTime occurredAt) {
}
