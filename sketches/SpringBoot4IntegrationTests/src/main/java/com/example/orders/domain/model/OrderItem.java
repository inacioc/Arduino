package com.example.orders.domain.model;

import java.math.BigDecimal;

public record OrderItem(
        String productId,
        String productName,
        int quantity,
        Money unitPrice
) {
    public OrderItem {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("Product ID cannot be blank");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (unitPrice == null) {
            throw new IllegalArgumentException("Unit price cannot be null");
        }
    }

    public Money subtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
