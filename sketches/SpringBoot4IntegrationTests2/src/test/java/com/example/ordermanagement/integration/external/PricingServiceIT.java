package com.example.ordermanagement.integration.external;

import com.example.ordermanagement.domain.model.Order;
import com.example.ordermanagement.domain.model.OrderItem;
import com.example.ordermanagement.infrastructure.adapter.out.external.PricingServiceAdapter;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the PricingServiceAdapter (outbound REST port).
 *
 * Uses WireMock as a local HTTP stub — replaces the real pricing service
 * without Docker, containers, or any external network dependency.
 *
 * @DynamicPropertySource injects the WireMock port into Spring context
 * so the adapter's RestClient picks it up at bean initialisation time.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class PricingServiceIT {

    private static WireMockServer wireMock;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    /** Injects WireMock's random port into Spring's environment before context starts. */
    @DynamicPropertySource
    static void overridePricingServiceUrl(DynamicPropertyRegistry registry) {
        registry.add("external.pricing-service.base-url", wireMock::baseUrl);
    }

    @Autowired
    private PricingServiceAdapter pricingServiceAdapter;

    // ── Happy path ──────────────────────────────────────────────────────────

    @Test
    void calculateTotalPrice_successfulResponse_returnsPriceFromService() {
        wireMock.stubFor(post(urlEqualTo("/api/pricing/calculate"))
                .withHeader("Content-Type", containing("application/json"))
                .willReturn(okJson("""
                        {"totalPrice": 129.95, "currency": "USD"}
                        """)));

        Order order = sampleOrder();
        BigDecimal result = pricingServiceAdapter.calculateTotalPrice(order);

        assertThat(result).isEqualByComparingTo("129.95");

        wireMock.verify(1, postRequestedFor(urlEqualTo("/api/pricing/calculate")));
    }

    @Test
    void calculateTotalPrice_requestBodyContainsOrderItems() {
        wireMock.stubFor(post(urlEqualTo("/api/pricing/calculate"))
                .willReturn(okJson("""
                        {"totalPrice": 59.97, "currency": "USD"}
                        """)));

        Order order = sampleOrder();
        pricingServiceAdapter.calculateTotalPrice(order);

        // Verify the request body includes the product IDs
        wireMock.verify(postRequestedFor(urlEqualTo("/api/pricing/calculate"))
                .withRequestBody(matchingJsonPath("$.orderId"))
                .withRequestBody(matchingJsonPath("$.items[0].productId", equalTo("PROD-1")))
                .withRequestBody(matchingJsonPath("$.items[0].quantity", equalTo("2"))));
    }

    // ── Resilience / fallback ───────────────────────────────────────────────

    @Test
    void calculateTotalPrice_serviceReturns500_fallsBackToLocalCalculation() {
        wireMock.stubFor(post(urlEqualTo("/api/pricing/calculate"))
                .willReturn(serverError()));

        Order order = sampleOrder();
        // fallback = sum of item.unitPrice * quantity  = 2 * 14.99 = 29.98
        BigDecimal result = pricingServiceAdapter.calculateTotalPrice(order);

        assertThat(result).isEqualByComparingTo("29.98");
    }

    @Test
    void calculateTotalPrice_serviceReturns404_fallsBackToLocalCalculation() {
        wireMock.stubFor(post(urlEqualTo("/api/pricing/calculate"))
                .willReturn(notFound()));

        Order order = sampleOrder();
        BigDecimal result = pricingServiceAdapter.calculateTotalPrice(order);

        // Fallback must still return a non-null positive amount
        assertThat(result).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void calculateTotalPrice_slowResponse_withinTimeout() {
        wireMock.stubFor(post(urlEqualTo("/api/pricing/calculate"))
                .willReturn(okJson("""
                        {"totalPrice": 29.98, "currency": "USD"}
                        """).withFixedDelay(200)));   // 200ms simulated latency

        Order order = sampleOrder();
        BigDecimal result = pricingServiceAdapter.calculateTotalPrice(order);

        assertThat(result).isEqualByComparingTo("29.98");
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private Order sampleOrder() {
        return Order.create("customer-pricing-test", List.of(
                new OrderItem("PROD-1", "Widget A", 2, new BigDecimal("14.99"))
        ));
    }
}
