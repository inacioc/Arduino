package com.example.ordermanagement.adapter.out.rest;

import com.example.ordermanagement.domain.port.out.ProductServicePort;
import com.example.ordermanagement.domain.port.out.ProductServicePort.ProductInfo;
import com.example.ordermanagement.infrastructure.adapter.out.rest.ProductRestAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Integration tests for ProductRestAdapter.
 *
 * Strategy:
 *  - @RestClientTest loads only ProductRestAdapter (Spring slice test).
 *  - MockRestServiceServer from spring-test intercepts RestClient calls
 *    at the request factory level — no HTTP server process needed.
 *  - The base URL is fixed via @TestPropertySource so stubs are predictable.
 *
 * This tests the full client stack in-process:
 *   RestClient.Builder config → URI resolution → request dispatch
 *   → MockRestServiceServer → response parsing → domain mapping.
 *
 * Compared to a full @SpringBootTest, this is intentionally narrow:
 * it validates the adapter in isolation without loading JPA, Security,
 * Batch, or IBM MQ context.
 */
@RestClientTest(ProductRestAdapter.class)
@TestPropertySource(properties = "app.product-service.base-url=http://product-service")
class ProductRestAdapterIT {

    @Autowired
    private MockRestServiceServer server;

    @Autowired
    private ProductServicePort productService;   // ProductRestAdapter implements this

    @AfterEach
    void verifyExpectations() {
        // Fails the test if a declared expectation was never triggered
        server.verify();
        server.reset();
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findProduct() returns mapped ProductInfo when service responds 200")
    void findProduct_success() {
        server.expect(once(), requestTo("http://product-service/products/PROD-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "id": "PROD-1",
                          "name": "Widget Alpha",
                          "price": 29.99,
                          "available": true
                        }
                        """, MediaType.APPLICATION_JSON));

        Optional<ProductInfo> result = productService.findProduct("PROD-1");

        assertThat(result).isPresent();
        assertThat(result.get().productId()).isEqualTo("PROD-1");
        assertThat(result.get().name()).isEqualTo("Widget Alpha");
        assertThat(result.get().price()).isEqualByComparingTo("29.99");
        assertThat(result.get().available()).isTrue();
    }

    // ── 404 — product not found ───────────────────────────────────────────────

    @Test
    @DisplayName("findProduct() returns empty Optional when service responds 404")
    void findProduct_notFound() {
        server.expect(once(), requestTo("http://product-service/products/PROD-UNKNOWN"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withResourceNotFound());

        Optional<ProductInfo> result = productService.findProduct("PROD-UNKNOWN");

        assertThat(result).isEmpty();
    }

    // ── Product unavailable ───────────────────────────────────────────────────

    @Test
    @DisplayName("findProduct() returns ProductInfo with available=false when out of stock")
    void findProduct_unavailable() {
        server.expect(once(), requestTo("http://product-service/products/PROD-OOS"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "id": "PROD-OOS",
                          "name": "Out of Stock Item",
                          "price": 9.99,
                          "available": false
                        }
                        """, MediaType.APPLICATION_JSON));

        Optional<ProductInfo> result = productService.findProduct("PROD-OOS");

        assertThat(result).isPresent();
        assertThat(result.get().available()).isFalse();
    }

    // ── Downstream service failure ────────────────────────────────────────────

    @Test
    @DisplayName("findProduct() returns empty Optional when service responds 500")
    void findProduct_serverError_returnsEmpty() {
        server.expect(once(), requestTo("http://product-service/products/PROD-ERR"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        Optional<ProductInfo> result = productService.findProduct("PROD-ERR");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findProduct() returns empty Optional when service responds 503")
    void findProduct_serviceUnavailable_returnsEmpty() {
        server.expect(once(), requestTo("http://product-service/products/PROD-DOWN"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServiceUnavailable());

        Optional<ProductInfo> result = productService.findProduct("PROD-DOWN");

        assertThat(result).isEmpty();
    }

    // ── Request shape verification ────────────────────────────────────────────

    @Test
    @DisplayName("findProduct() sends a GET to /products/{id} with no request body")
    void findProduct_requestShape() {
        server.expect(once(), requestTo("http://product-service/products/my-product"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(content().string(""))   // no request body on GET
                .andRespond(withSuccess("""
                        {"id":"my-product","name":"X","price":1.00,"available":true}
                        """, MediaType.APPLICATION_JSON));

        productService.findProduct("my-product");
        // server.verify() in @AfterEach confirms the request was made
    }
}
