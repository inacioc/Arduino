package com.example.ordermanagement.frontend.client;

import com.example.ordermanagement.frontend.client.dto.CreateOrderRequestDto;
import com.example.ordermanagement.frontend.client.dto.OrderDto;
import com.example.ordermanagement.frontend.client.dto.OrderStatus;
import com.example.ordermanagement.frontend.client.exception.BackendNotFoundException;
import com.example.ordermanagement.frontend.client.exception.BackendUnavailableException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Talks HTTP + JSON to adapter-in-web's {@code /api/orders}. The REST API has
 * no "find all orders" endpoint (only find-by-status, find-by-id, and
 * find-by-customer, mirroring the domain's own inbound port) - {@link #findAll()}
 * reproduces the frontend's unfiltered order list the same way the REST
 * controller itself would have to: querying every status and merging.
 */
@Component
public class OrderApiClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OrderApiClient(RestClient backendRestClient, ObjectMapper objectMapper) {
        this.restClient = backendRestClient;
        this.objectMapper = objectMapper;
    }

    public OrderDto create(CreateOrderRequestDto request) {
        return execute(() -> restClient.post()
                .uri("/api/orders")
                .body(request)
                .retrieve()
                .body(OrderDto.class));
    }

    public Optional<OrderDto> findById(UUID id) {
        try {
            return Optional.ofNullable(execute(() -> restClient.get()
                    .uri("/api/orders/{id}", id)
                    .retrieve()
                    .body(OrderDto.class)));
        } catch (BackendNotFoundException e) {
            return Optional.empty();
        }
    }

    public List<OrderDto> findByStatus(OrderStatus status) {
        return execute(() -> restClient.get()
                .uri("/api/orders?status={status}", status.name())
                .retrieve()
                .body(new ParameterizedTypeReference<List<OrderDto>>() {}));
    }

    public List<OrderDto> findAll() {
        return Arrays.stream(OrderStatus.values())
                .flatMap(status -> findByStatus(status).stream())
                .sorted(Comparator.comparing(OrderDto::createdAt).reversed())
                .toList();
    }

    public OrderDto confirm(UUID id) {
        return execute(() -> restClient.put().uri("/api/orders/{id}/confirm", id).retrieve().body(OrderDto.class));
    }

    public OrderDto complete(UUID id) {
        return execute(() -> restClient.put().uri("/api/orders/{id}/complete", id).retrieve().body(OrderDto.class));
    }

    public OrderDto cancel(UUID id) {
        return execute(() -> restClient.put().uri("/api/orders/{id}/cancel", id).retrieve().body(OrderDto.class));
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
