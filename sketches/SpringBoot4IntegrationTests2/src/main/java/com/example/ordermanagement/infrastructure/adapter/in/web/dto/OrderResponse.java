package com.example.ordermanagement.infrastructure.adapter.in.web.dto;

import com.example.ordermanagement.domain.model.Order;
import com.example.ordermanagement.domain.model.OrderItem;
import com.example.ordermanagement.domain.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String customerId,
        List<OrderItemResponse> items,
        OrderStatus status,
        BigDecimal totalPrice,
        Instant createdAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getItems().stream().map(OrderItemResponse::from).toList(),
                order.getStatus(),
                order.getTotalPrice(),
                order.getCreatedAt()
        );
    }

    public record OrderItemResponse(
            String productId,
            String productName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal
    ) {
        public static OrderItemResponse from(OrderItem item) {
            return new OrderItemResponse(
                    item.productId(), item.productName(),
                    item.quantity(), item.unitPrice(), item.subtotal()
            );
        }
    }
}
