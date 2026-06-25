package com.example.ordermanagement.domain.service;

import com.example.ordermanagement.domain.model.Product;
import com.example.ordermanagement.domain.port.in.GetProductUseCase;
import com.example.ordermanagement.domain.port.in.ProductResult;
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
 * {@link ProductRepositoryPort} for persistence and exposes the result as
 * strict {@link ProductResult} DTOs, so the rich {@link Product} aggregate
 * never leaks past the inbound ports.
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
    public ProductResult save(SaveProductCommand command) {
        Product product = Product.create(
                command.id(),
                command.name(),
                command.price(),
                command.available()
        );
        return ProductResult.from(productRepository.save(product));
    }

    // ── GetProductUseCase ───────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductResult> findProduct(UUID productId) {
        return productRepository.findById(productId).map(ProductResult::from);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResult> findAll() {
        return productRepository.findAll().stream()
                .map(ProductResult::from)
                .toList();
    }
}
