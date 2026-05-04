package com.example.ordermanagement.infrastructure.adapter.out.external.dto;

import com.example.ordermanagement.domain.model.Order;

import java.util.List;
import java.util.UUID;

public record PricingRequest(
        UUID orderId,
        String customerId,
        List<PricingItemDto> items
) {
    public static PricingRequest from(Order order) {
        return new PricingRequest(
                order.getId(),
                order.getCustomerId(),
                order.getItems().stream()
                        .map(i -> new PricingItemDto(i.productId(), i.quantity(), i.unitPrice()))
                        .toList()
        );
    }

    public record PricingItemDto(String productId, int quantity, java.math.BigDecimal unitPrice) {}
}
