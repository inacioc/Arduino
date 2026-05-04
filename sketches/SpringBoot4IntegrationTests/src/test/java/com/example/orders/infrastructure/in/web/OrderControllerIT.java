package com.example.orders.infrastructure.in.web;

import com.example.orders.AbstractIntegrationTest;
import com.example.orders.domain.port.in.CreateOrderUseCase;
import com.example.orders.domain.port.out.InventoryPort;
import com.example.orders.infrastructure.in.web.dto.CreateOrderRequest;
import com.example.orders.infrastructure.in.web.dto.OrderResponse;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Full-stack integration tests for OrderController.
 *
 * The application context is fully loaded against real PostgreSQL, Kafka, and Keycloak
 * containers (inherited from AbstractIntegrationTest).
 *
 * The external Inventory service is mocked with @MockitoBean so that HTTP is not
 * required for the inventory check — that scenario is tested in InventoryRestAdapterIT.
 */
class OrderControllerIT extends AbstractIntegrationTest {

    // Avoids real HTTP call to inventory; that adapter is tested separately
    @MockitoBean
    private InventoryPort inventoryPort;

    @BeforeEach
    void configureInventory() {
        // Default: inventory is available for all products
        when(inventoryPort.isAvailable(anyString(), anyInt())).thenReturn(true);
    }

    // ── POST /api/orders ─────────────────────────────────────────────────────

    @Nested
    class CreateOrder {

        @Test
        void returns201AndOrderBodyForAuthenticatedUser() {
            String token = getAccessToken("test-user", "test-password");

            var request = new CreateOrderRequest(
                    "customer-001",
                    List.of(new CreateOrderRequest.ItemRequest(
                            "prod-1", "Blue Widget", 3, 19.99, "USD"))
            );

            webTestClient.post()
                    .uri("/api/orders")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectHeader().exists("Location")
                    .expectBody(OrderResponse.class)
                    .value(resp -> {
                        assertThat(resp.id()).isNotBlank();
                        assertThat(resp.customerId()).isEqualTo("customer-001");
                        assertThat(resp.status()).isEqualTo("PENDING");
                        assertThat(resp.totalAmount()).isPositive();
                        assertThat(resp.items()).hasSize(1);
                    });
        }

        @Test
        void returns401WhenNoTokenIsPresent() {
            var request = new CreateOrderRequest(
                    "customer-001",
                    List.of(new CreateOrderRequest.ItemRequest(
                            "prod-1", "Blue Widget", 1, 9.99, "USD"))
            );

            webTestClient.post()
                    .uri("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        void returns422WhenInventoryIsInsufficient() {
            when(inventoryPort.isAvailable("out-of-stock", anyInt())).thenReturn(false);

            String token = getAccessToken("test-user", "test-password");
            var request = new CreateOrderRequest(
                    "customer-002",
                    List.of(new CreateOrderRequest.ItemRequest(
                            "out-of-stock", "Rare Item", 5, 99.99, "USD"))
            );

            webTestClient.post()
                    .uri("/api/orders")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isEqualTo(422)
                    .expectBody()
                    .jsonPath("$.title").isEqualTo("Insufficient Inventory");
        }

        @Test
        void returns400ForInvalidRequestBody() {
            String token = getAccessToken("test-user", "test-password");

            // Missing required fields — empty items list
            webTestClient.post()
                    .uri("/api/orders")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {"customerId": "", "items": []}
                            """)
                    .exchange()
                    .expectStatus().isBadRequest();
        }
    }

    // ── GET /api/orders/{id} ─────────────────────────────────────────────────

    @Nested
    class GetOrder {

        @Test
        void returns200WithOrderForExistingId() {
            String token = getAccessToken("test-user", "test-password");

            // Create an order first to have a known ID
            var created = webTestClient.post()
                    .uri("/api/orders")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new CreateOrderRequest(
                            "customer-get",
                            List.of(new CreateOrderRequest.ItemRequest(
                                    "prod-get", "Widget", 1, 10.00, "USD"))))
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody(OrderResponse.class)
                    .returnResult().getResponseBody();

            assertThat(created).isNotNull();

            webTestClient.get()
                    .uri("/api/orders/{id}", created.id())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(OrderResponse.class)
                    .value(resp -> assertThat(resp.id()).isEqualTo(created.id()));
        }

        @Test
        void returns404ForUnknownId() {
            String token = getAccessToken("test-user", "test-password");

            webTestClient.get()
                    .uri("/api/orders/non-existent-id")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .exchange()
                    .expectStatus().isNotFound();
        }

        @Test
        void returns401WhenNoTokenIsPresent() {
            webTestClient.get()
                    .uri("/api/orders/some-id")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }
}
