package com.example.orders.infrastructure.in.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record CreateOrderRequest(
        @NotBlank String customerId,
        @NotEmpty @Valid List<ItemRequest> items
) {
    public record ItemRequest(
            @NotBlank String productId,
            @NotBlank String productName,
            @Positive int quantity,
            @Positive double unitPrice,
            @NotBlank String currency
    ) {}
}
