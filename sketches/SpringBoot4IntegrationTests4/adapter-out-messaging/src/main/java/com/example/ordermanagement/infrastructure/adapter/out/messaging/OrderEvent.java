package com.example.ordermanagement.infrastructure.adapter.out.messaging;

import com.example.ordermanagement.domain.model.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record OrderEvent(
        String eventType,
        UUID orderId,
        String customerId,
        OrderStatus status,
        BigDecimal totalAmount,
        LocalDateTime occurredAt
) {
    public static final String ORDER_CREATED   = "ORDER_CREATED";
    public static final String ORDER_COMPLETED = "ORDER_COMPLETED";
}
