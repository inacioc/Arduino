package com.example.orders.infrastructure.out.client;

import com.example.orders.domain.port.out.InventoryPort;
import com.example.orders.infrastructure.out.client.dto.InventoryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class InventoryRestAdapter implements InventoryPort {

    private static final Logger log = LoggerFactory.getLogger(InventoryRestAdapter.class);

    private final RestClient restClient;

    public InventoryRestAdapter(RestClient.Builder restClientBuilder,
                                @Value("${inventory.service.url}") String inventoryServiceUrl) {
        this.restClient = restClientBuilder
                .baseUrl(inventoryServiceUrl)
                .build();
    }

    @Override
    public boolean isAvailable(String productId, int requestedQuantity) {
        try {
            InventoryResponse response = restClient.get()
                    .uri("/api/inventory/{productId}", productId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new InventoryServiceException(
                                "Product not found or inventory unavailable: " + productId);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new InventoryServiceException("Inventory service error for product: " + productId);
                    })
                    .body(InventoryResponse.class);

            return response != null && response.available() && response.quantity() >= requestedQuantity;
        } catch (InventoryServiceException e) {
            log.warn("Inventory check failed for product {}: {}", productId, e.getMessage());
            return false;
        }
    }
}
