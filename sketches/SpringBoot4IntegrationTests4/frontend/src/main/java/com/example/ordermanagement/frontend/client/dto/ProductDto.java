package com.example.ordermanagement.frontend.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Mirrors adapter-in-web's {@code ProductResponse} JSON shape. */
public record ProductDto(
        UUID id,
        String name,
        BigDecimal price,
        boolean available
) {}
