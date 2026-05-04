package com.example.ordermanagement.infrastructure.adapter.out.messaging.dto;

import com.example.ordermanagement.domain.model.Order;

import java.time.Instant;
import java.util.UUID;

public record OrderEventMessage(
        UUID orderId,
        String customerId,
        String status,
        String eventType,
        Instant eventTime
) {
    public static OrderEventMessage from(Order order, String eventType) {
        return new OrderEventMessage(
                order.getId(),
                order.getCustomerId(),
                order.getStatus().name(),
                eventType,
                Instant.now()
        );
    }
}
