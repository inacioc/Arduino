package com.example.ordermanagement.frontend.client.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Mirrors adapter-in-web's {@code CreateOrderRequest}/{@code OrderItemRequest} JSON shape. */
public record CreateOrderRequestDto(
        String customerId,
        List<OrderItemRequestDto> items
) {
    public record OrderItemRequestDto(
            UUID productId,
            int quantity,
            BigDecimal unitPrice
    ) {}
}
