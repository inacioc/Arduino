package com.example.ordermanagement.domain.port.in;

import com.example.ordermanagement.domain.model.Product;

import java.util.Optional;

/**
 * Inbound port for reading products as domain {@link Product} aggregates.
 * The catalogue still lives in an external service; this use case is the
 * domain-facing entry point that hides that detail from callers.
 */
public interface GetProductUseCase {

    Optional<Product> findProduct(String productId);
}
