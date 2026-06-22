package com.example.ordermanagement.infrastructure.adapter.out.rest;

import com.example.ordermanagement.domain.port.out.ProductServicePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Component
public class ProductRestAdapter implements ProductServicePort {

    private static final Logger log = LoggerFactory.getLogger(ProductRestAdapter.class);

    private final RestClient restClient;

    public ProductRestAdapter(RestClient.Builder restClientBuilder,
                              @Value("${app.product-service.base-url}") String baseUrl) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .build();
    }

    @Override
    public Optional<ProductInfo> findProduct(String productId) {
        try {
            ProductDto dto = restClient.get()
                    .uri("/products/{id}", productId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        // swallow 404 — return empty Optional instead of throwing
                    })
                    .body(ProductDto.class);

            if (dto == null) {
                return Optional.empty();
            }

            return Optional.of(new ProductInfo(dto.id(), dto.name(), dto.price(), dto.available()));

        } catch (Exception e) {
            log.warn("Failed to fetch product {}: {}", productId, e.getMessage());
            return Optional.empty();
        }
    }
}
