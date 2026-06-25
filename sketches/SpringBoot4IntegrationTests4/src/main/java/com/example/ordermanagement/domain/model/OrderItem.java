package com.example.ordermanagement.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

public class OrderItem {

    private final UUID productId;
    private final String productName;
    private final int quantity;
    private final BigDecimal unitPrice;

    public OrderItem(UUID productId, String productName, int quantity, BigDecimal unitPrice) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Unit price must be positive");
        }
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public BigDecimal subtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public UUID getProductId()     { return productId; }
    public String getProductName() { return productName; }
    public int getQuantity()       { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
}
