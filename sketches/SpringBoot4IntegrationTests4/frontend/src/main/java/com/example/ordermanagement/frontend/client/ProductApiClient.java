package com.example.ordermanagement.frontend.client;

import com.example.ordermanagement.frontend.client.dto.CreateProductRequestDto;
import com.example.ordermanagement.frontend.client.dto.ProductDto;
import com.example.ordermanagement.frontend.client.exception.BackendNotFoundException;
import com.example.ordermanagement.frontend.client.exception.BackendUnavailableException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Talks HTTP + JSON to adapter-in-web's {@code /api/products} - the only way
 * this module knows products exist at all.
 */
@Component
public class ProductApiClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public ProductApiClient(RestClient backendRestClient, ObjectMapper objectMapper) {
        this.restClient = backendRestClient;
        this.objectMapper = objectMapper;
    }

    public ProductDto create(CreateProductRequestDto request) {
        return execute(() -> restClient.post()
                .uri("/api/products")
                .body(request)
                .retrieve()
                .body(ProductDto.class));
    }

    public Optional<ProductDto> findById(UUID id) {
        try {
            return Optional.ofNullable(execute(() -> restClient.get()
                    .uri("/api/products/{id}", id)
                    .retrieve()
                    .body(ProductDto.class)));
        } catch (BackendNotFoundException e) {
            return Optional.empty();
        }
    }

    public List<ProductDto> findAll() {
        return execute(() -> restClient.get()
                .uri("/api/products")
                .retrieve()
                .body(new ParameterizedTypeReference<List<ProductDto>>() {}));
    }

    private <T> T execute(Supplier<T> call) {
        try {
            return call.get();
        } catch (RestClientResponseException ex) {
            throw ProblemDetailTranslator.translate(ex, objectMapper);
        } catch (RestClientException ex) {
            throw new BackendUnavailableException("Could not reach the order service", ex);
        }
    }
}
