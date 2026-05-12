package com.example.ordermanagement.domain.port.out;

import java.math.BigDecimal;
import java.util.Optional;

public interface ProductServicePort {

    Optional<ProductInfo> findProduct(String productId);

    record ProductInfo(
            String productId,
            String name,
            BigDecimal price,
            boolean available
    ) {}
}
