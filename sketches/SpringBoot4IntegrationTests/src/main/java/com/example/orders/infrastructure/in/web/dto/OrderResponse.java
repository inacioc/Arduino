package com.example.orders.infrastructure.in.web.dto;

import com.example.orders.domain.model.Order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        String id,
        String customerId,
        String status,
        BigDecimal totalAmount,
        String currency,
        LocalDateTime createdAt,
        List<ItemResponse> items
) {
    public record ItemResponse(
            String productId,
            String productName,
            int quantity,
            BigDecimal unitPrice,
            String currency
    ) {}

    public static OrderResponse from(Order order) {
        List<ItemResponse> itemResponses = order.getItems().stream()
                .map(i -> new ItemResponse(
                        i.productId(),
                        i.productName(),
                        i.quantity(),
                        i.unitPrice().amount(),
                        i.unitPrice().currency()
                ))
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStatus().name(),
                order.total().amount(),
                order.total().currency(),
                order.getCreatedAt(),
                itemResponses
        );
    }
}
