package com.example.ordermanagement.order.internal.adapter.out.messaging;

import com.example.ordermanagement.order.internal.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record OrderEvent(
        String eventType,
        UUID orderId,
        UUID customerId,
        OrderStatus status,
        BigDecimal totalAmount,
        LocalDateTime occurredAt
) {
    public static final String ORDER_CREATED   = "ORDER_CREATED";
    public static final String ORDER_COMPLETED = "ORDER_COMPLETED";
}
