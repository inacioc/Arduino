package com.example.ordermanagement.infrastructure.adapter.out.external;

import com.example.ordermanagement.domain.model.Order;
import com.example.ordermanagement.domain.port.out.PricingServicePort;
import com.example.ordermanagement.infrastructure.adapter.out.external.dto.PricingRequest;
import com.example.ordermanagement.infrastructure.adapter.out.external.dto.PricingResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;

/**
 * Calls the external Pricing Service via REST.
 * Uses Spring's RestClient (the modern replacement for RestTemplate).
 * The "navr" library used by the team is wrapped inside this adapter —
 * isolating infrastructure concerns from the domain.
 */
@Component
public class PricingServiceAdapter implements PricingServicePort {

    private static final Logger log = LoggerFactory.getLogger(PricingServiceAdapter.class);

    private final RestClient restClient;

    public PricingServiceAdapter(
            RestClient.Builder builder,
            @Value("${external.pricing-service.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @Override
    public BigDecimal calculateTotalPrice(Order order) {
        try {
            PricingRequest request = PricingRequest.from(order);
            PricingResponse response = restClient.post()
                    .uri("/api/pricing/calculate")
                    .body(request)
                    .retrieve()
                    .body(PricingResponse.class);

            return response != null ? response.totalPrice() : BigDecimal.ZERO;
        } catch (RestClientException ex) {
            log.warn("Pricing service unavailable for orderId={}, using fallback price", order.getId(), ex);
            return order.getItems().stream()
                    .map(i -> i.unitPrice().multiply(BigDecimal.valueOf(i.quantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }
}
