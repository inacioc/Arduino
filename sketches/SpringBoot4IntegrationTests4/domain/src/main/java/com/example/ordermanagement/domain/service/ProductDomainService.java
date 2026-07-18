package com.example.ordermanagement.domain.service;

import com.example.ordermanagement.domain.model.Product;
import com.example.ordermanagement.domain.port.in.GetProductUseCase;
import com.example.ordermanagement.domain.port.in.SaveProductUseCase;
import com.example.ordermanagement.domain.port.out.ProductRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain service for products.
 * <p>
 * Mirrors {@link OrderDomainService}: it drives the outbound
 * {@link ProductRepositoryPort} for persistence and returns the rich
 * {@link Product} aggregate directly (pragmatic style). Driving adapters map
 * the aggregate to their transport DTOs at the edge.
 */
@Service
@Transactional
public class ProductDomainService implements GetProductUseCase, SaveProductUseCase {

    private final ProductRepositoryPort productRepository;

    public ProductDomainService(ProductRepositoryPort productRepository) {
        this.productRepository = productRepository;
    }

    // ── SaveProductUseCase ──────────────────────────────────────────────────────

    @Override
    public Product save(SaveProductCommand command) {
        Product product = Product.create(
                command.id(),
                command.name(),
                command.price(),
                command.available()
        );
        return productRepository.save(product);
    }

    // ── GetProductUseCase ───────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> findProduct(UUID productId) {
        return productRepository.findById(productId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> findAll() {
        return productRepository.findAll();
    }
}
