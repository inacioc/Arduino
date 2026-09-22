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
        //
        // saveAndFlush (not save) is required here: plain save() only enqueues the write in
        // the persistence context - Hibernate defers the actual INSERT until the next flush,
        // which by default happens at @Transactional commit, i.e. AFTER this method (and its
        // try/catch in executeAndTranslate) has already returned. A unique-constraint
        // violation would then surface outside this adapter entirely, unmediated by
        // executeAndTranslate, and never become a ProductAlreadyExistsException. Flushing here
        // forces the INSERT - and therefore the constraint check - to happen synchronously,
        // inside the try/catch, where it can actually be translated.
        return executeAndTranslate(
                () -> mapper.toDomain(jpaRepository.saveAndFlush(mapper.toEntity(product))),
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
    public Optional<Product> findByName(String name) {
        return executeAndTranslate(
                () -> jpaRepository.findByName(name).map(mapper::toDomain),
                "Product", name);
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
