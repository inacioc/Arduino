package com.example.ordermanagement.infrastructure.adapter.out.rest;

import java.math.BigDecimal;

public record ProductDto(
        String id,
        String name,
        BigDecimal price,
        boolean available
) {}
