package com.example.ordermanagement.domain.service;

import com.example.ordermanagement.domain.model.Product;
import com.example.ordermanagement.domain.port.in.GetProductUseCase;
import com.example.ordermanagement.domain.port.out.ProductServicePort;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Domain service for products.
 * <p>
 * Mirrors {@link OrderDomainService}: it drives an outbound port and exposes the
 * result as a rich domain {@link Product}. The external product catalogue returns
 * the anemic {@link ProductServicePort.ProductInfo} DTO; this service maps it into
 * the domain model so the rest of the application never sees the port DTO.
 * <p>
 * Currently stands alone — {@code OrderDomainService} still talks to
 * {@link ProductServicePort} directly.
 */
@Service
public class ProductDomainService implements GetProductUseCase {

    private final ProductServicePort productService;

    public ProductDomainService(ProductServicePort productService) {
        this.productService = productService;
    }

    @Override
    public Optional<Product> findProduct(String productId) {
        return productService.findProduct(productId).map(this::toDomain);
    }

    private Product toDomain(ProductServicePort.ProductInfo info) {
        return Product.create(
                info.productId(),
                info.name(),
                info.price(),
                info.available()
        );
    }
}
