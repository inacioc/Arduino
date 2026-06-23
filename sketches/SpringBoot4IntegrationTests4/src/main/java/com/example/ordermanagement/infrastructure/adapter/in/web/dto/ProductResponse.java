package com.example.ordermanagement.infrastructure.adapter.in.web.dto;

import com.example.ordermanagement.domain.port.in.ProductResult;

import java.math.BigDecimal;

public record ProductResponse(
        String id,
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
