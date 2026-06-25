package com.example.ordermanagement.domain.port.in;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Inbound port for creating/updating products in the local catalogue.
 * Accepts a plain command and returns a result DTO — no domain type crosses the port.
 */
public interface SaveProductUseCase {

    ProductResult save(SaveProductCommand command);

    record SaveProductCommand(
            UUID id,
            String name,
            BigDecimal price,
            boolean available
    ) {}
}
