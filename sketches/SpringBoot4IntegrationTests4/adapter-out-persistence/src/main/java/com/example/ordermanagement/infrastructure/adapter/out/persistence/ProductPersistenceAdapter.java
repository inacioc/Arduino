package com.example.ordermanagement.infrastructure.adapter.out.persistence;

import com.example.ordermanagement.domain.exception.ProductAlreadyExistsException;
import com.example.ordermanagement.domain.model.Product;
import com.example.ordermanagement.domain.port.out.ProductRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ProductPersistenceAdapter extends AbstractPersistenceAdapter implements ProductRepositoryPort {

    private final ProductJpaRepository jpaRepository;
    private final ProductEntityMapper mapper;

    public ProductPersistenceAdapter(ProductJpaRepository jpaRepository, ProductEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper        = mapper;
    }

    @Override
    public Product save(Product product) {
        // Product.id is a client-supplied UUID that JPA treats as an upsert key (re-saving
        // the same id updates the row - see this adapter's own tests), so the duplicate
        // that can actually occur here is on name (enforced by migration V3), not id.
        return executeAndTranslate(
                () -> mapper.toDomain(jpaRepository.save(mapper.toEntity(product))),
                "Product",
                product.getName(),
                (entityName, name) -> new ProductAlreadyExistsException(name));
    }

    @Override
    public Optional<Product> findById(UUID id) {
        return executeAndTranslate(
                () -> jpaRepository.findById(id).map(mapper::toDomain),
                "Product", id.toString());
    }

    @Override
    public List<Product> findAll() {
        return executeAndTranslate(
                () -> jpaRepository.findAll().stream().map(mapper::toDomain).toList(),
                "Product", "all");
    }

    @Override
    public void deleteById(UUID id) {
        executeAndTranslate(() -> {
            jpaRepository.deleteById(id);
            return null;
        }, "Product", id.toString());
    }
}
