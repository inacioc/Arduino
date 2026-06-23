package com.example.ordermanagement.infrastructure.adapter.out.persistence;

import com.example.ordermanagement.domain.model.Product;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProductEntityMapper {

    // ── Domain → Entity ───────────────────────────────────────────────────────

    ProductEntity toEntity(Product product);

    // ── Entity → Domain ───────────────────────────────────────────────────────

    default Product toDomain(ProductEntity entity) {
        return Product.create(
                entity.getId(),
                entity.getName(),
                entity.getPrice(),
                entity.isAvailable()
        );
    }
}
