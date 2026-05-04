package com.example.ordermanagement.infrastructure.adapter.in.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateOrderRequest(
        @NotEmpty @Valid List<OrderItemRequest> items
) {
    public record OrderItemRequest(
            @NotBlank String productId,
            @NotBlank String productName,
            @Min(1) int quantity
    ) {}
}
