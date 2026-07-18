package com.example.ordermanagement.infrastructure.adapter.in.web.dto;

import com.example.ordermanagement.domain.model.Product;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String name,
        BigDecimal price,
        boolean available
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.isAvailable()
        );
    }
}
