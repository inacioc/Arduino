package com.example.ordermanagement.domain.port.in;

import com.example.ordermanagement.domain.model.Product;

import java.util.List;
import java.util.Optional;

/**
 * Inbound port for reading persisted products.
 * <p>
 * Pragmatic style: the use case returns the rich {@link Product} aggregate
 * directly; the driving adapter maps it to a transport DTO at the edge.
 */
public interface GetProductUseCase {

    Optional<Product> findProduct(String productId);

    List<Product> findAll();
}
