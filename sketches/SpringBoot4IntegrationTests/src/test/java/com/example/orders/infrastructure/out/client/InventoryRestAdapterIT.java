package com.example.orders.infrastructure.out.client;

import com.example.orders.infrastructure.config.RestClientConfig;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for InventoryRestAdapter.
 *
 * Uses WireMock Spring Boot to spin up a stub HTTP server and injects its URL
 * into the inventory.service.url property automatically.
 *
 * Loads a minimal slice (no DB, no Kafka, no Keycloak) — just the adapter and
 * its RestClient dependency, making the test fast and isolated.
 */
@SpringBootTest(
        classes = {InventoryRestAdapter.class, RestClientConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("test")
@EnableWireMock(
        @ConfigureWireMock(name = "inventory-service", property = "inventory.service.url")
)
class InventoryRestAdapterIT {

    @InjectWireMock("inventory-service")
    WireMockServer inventoryWireMock;

    @Autowired
    InventoryRestAdapter inventoryAdapter;

    // ── isAvailable ──────────────────────────────────────────────────────────

    @Test
    void returnsTrueWhenProductIsAvailableWithSufficientStock() {
        inventoryWireMock.stubFor(get(urlEqualTo("/api/inventory/prod-1"))
                .willReturn(okJson("""
                        {"productId":"prod-1","available":true,"quantity":100}
                        """)));

        boolean result = inventoryAdapter.isAvailable("prod-1", 10);

        assertThat(result).isTrue();
        inventoryWireMock.verify(1, getRequestedFor(urlEqualTo("/api/inventory/prod-1")));
    }

    @Test
    void returnsFalseWhenProductIsMarkedUnavailable() {
        inventoryWireMock.stubFor(get(urlEqualTo("/api/inventory/prod-2"))
                .willReturn(okJson("""
                        {"productId":"prod-2","available":false,"quantity":0}
                        """)));

        boolean result = inventoryAdapter.isAvailable("prod-2", 1);

        assertThat(result).isFalse();
    }

    @Test
    void returnsFalseWhenRequestedQuantityExceedsStock() {
        inventoryWireMock.stubFor(get(urlEqualTo("/api/inventory/prod-3"))
                .willReturn(okJson("""
                        {"productId":"prod-3","available":true,"quantity":5}
                        """)));

        // Requesting 10 but only 5 in stock
        boolean result = inventoryAdapter.isAvailable("prod-3", 10);

        assertThat(result).isFalse();
    }

    @Test
    void returnsFalseGracefullyWhenInventoryServiceReturns404() {
        inventoryWireMock.stubFor(get(urlEqualTo("/api/inventory/unknown"))
                .willReturn(aResponse().withStatus(404)));

        boolean result = inventoryAdapter.isAvailable("unknown", 1);

        assertThat(result).isFalse();
    }

    @Test
    void returnsFalseGracefullyWhenInventoryServiceReturns500() {
        inventoryWireMock.stubFor(get(urlEqualTo("/api/inventory/prod-err"))
                .willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

        boolean result = inventoryAdapter.isAvailable("prod-err", 1);

        assertThat(result).isFalse();
    }

    @Test
    void sendsCorrectAcceptHeaderToInventoryService() {
        inventoryWireMock.stubFor(get(urlEqualTo("/api/inventory/prod-hdr"))
                .willReturn(okJson("""
                        {"productId":"prod-hdr","available":true,"quantity":50}
                        """)));

        inventoryAdapter.isAvailable("prod-hdr", 1);

        inventoryWireMock.verify(getRequestedFor(urlEqualTo("/api/inventory/prod-hdr"))
                .withHeader("Accept", containing("application/json")));
    }
}
