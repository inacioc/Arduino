package com.example.ordermanagement.domain.model;

import java.math.BigDecimal;

public record OrderItem(
        String productId,
        String productName,
        int quantity,
        BigDecimal unitPrice
) {
    public OrderItem {
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");
        if (unitPrice == null || unitPrice.signum() < 0) throw new IllegalArgumentException("Unit price cannot be negative");
    }

    public BigDecimal subtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
