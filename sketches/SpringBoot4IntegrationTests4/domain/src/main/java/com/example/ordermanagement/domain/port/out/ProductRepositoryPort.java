package com.example.ordermanagement.domain.port.out;

import com.example.ordermanagement.domain.model.Product;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for persisting products.
 * <p>
 * Like {@link OrderRepositoryPort}, this is a driven port: it is owned by the
 * domain and therefore speaks the domain language ({@link Product}). The JPA
 * details live in the persistence adapter that implements it.
 */
public interface ProductRepositoryPort {

    Product save(Product product);

    Optional<Product> findById(UUID id);

    List<Product> findAll();

    void deleteById(UUID id);
}
