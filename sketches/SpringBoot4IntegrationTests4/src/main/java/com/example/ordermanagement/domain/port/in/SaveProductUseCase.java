package com.example.ordermanagement.domain.port.in;

import com.example.ordermanagement.domain.model.Product;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Inbound port for creating/updating products in the local catalogue.
 * Returns the persisted {@link Product} aggregate (pragmatic style).
 */
public interface SaveProductUseCase {

    Product save(SaveProductCommand command);

    record SaveProductCommand(
            UUID id,
            String name,
            BigDecimal price,
            boolean available
    ) {}
}
