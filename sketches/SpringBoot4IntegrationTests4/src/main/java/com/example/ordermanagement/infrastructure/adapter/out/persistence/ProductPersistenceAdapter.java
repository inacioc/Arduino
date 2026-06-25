package com.example.ordermanagement.infrastructure.adapter.out.persistence;

import com.example.ordermanagement.domain.model.Product;
import com.example.ordermanagement.domain.port.out.ProductRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ProductPersistenceAdapter implements ProductRepositoryPort {

    private final ProductJpaRepository jpaRepository;
    private final ProductEntityMapper mapper;

    public ProductPersistenceAdapter(ProductJpaRepository jpaRepository, ProductEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper        = mapper;
    }

    @Override
    public Product save(Product product) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(product)));
    }

    @Override
    public Optional<Product> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<Product> findAll() {
        return jpaRepository.findAll().stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }
}
