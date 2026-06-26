package com.example.ordermanagement.infrastructure.adapter.in.web.dto;

import com.example.ordermanagement.domain.port.in.ProductResult;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String name,
        BigDecimal price,
        boolean available
) {
    public static ProductResponse from(ProductResult product) {
        return new ProductResponse(
                product.id(),
                product.name(),
                product.price(),
                product.available()
        );
    }
}
