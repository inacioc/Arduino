package com.example.ordermanagement.domain.port.in;

import java.util.List;
import java.util.Optional;

/**
 * Inbound port for reading persisted products as result DTOs.
 * The rich {@code Product} aggregate stays inside the hexagon.
 */
public interface GetProductUseCase {

    Optional<ProductResult> findProduct(String productId);

    List<ProductResult> findAll();
}
