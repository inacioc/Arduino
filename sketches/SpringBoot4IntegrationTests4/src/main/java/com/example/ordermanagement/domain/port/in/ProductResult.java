package com.example.ordermanagement.domain.port.in;

import com.example.ordermanagement.domain.model.Product;

import java.math.BigDecimal;

/**
 * Read-only result exposed by the inbound product use cases.
 * <p>
 * Mirrors {@link OrderResult}: the rich {@link Product} aggregate never leaves
 * the hexagon — callers see this DTO instead.
 */
public record ProductResult(
        String id,
        String name,
        BigDecimal price,
        boolean available
) {
    public static ProductResult from(Product product) {
        return new ProductResult(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.isAvailable()
        );
    }
}
