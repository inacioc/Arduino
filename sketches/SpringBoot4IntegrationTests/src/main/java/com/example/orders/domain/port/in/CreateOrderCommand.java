package com.example.orders.domain.port.in;

import com.example.orders.domain.model.OrderItem;

import java.util.List;

public record CreateOrderCommand(
        String customerId,
        List<OrderItem> items
) {
    public CreateOrderCommand {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("Customer ID is required");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("At least one order item is required");
        }
    }
}
