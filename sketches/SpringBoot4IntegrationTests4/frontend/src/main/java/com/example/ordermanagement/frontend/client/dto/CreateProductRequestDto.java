package com.example.ordermanagement.frontend.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Mirrors adapter-in-web's {@code CreateProductRequest} JSON shape. */
public record CreateProductRequestDto(
        UUID id,
        String name,
        BigDecimal price,
        boolean available
) {}
