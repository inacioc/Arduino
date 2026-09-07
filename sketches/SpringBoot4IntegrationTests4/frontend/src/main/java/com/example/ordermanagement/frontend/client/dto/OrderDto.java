package com.example.ordermanagement.frontend.client.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Mirrors adapter-in-web's {@code OrderResponse} JSON shape. */
public record OrderDto(
        UUID id,
        String customerId,
        OrderStatus status,
        BigDecimal totalAmount,
        List<OrderItemDto> items,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record OrderItemDto(
            UUID productId,
            String productName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal
    ) {}
}
