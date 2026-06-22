package com.example.ordermanagement.infrastructure.adapter.in.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateOrderRequest(

        @NotBlank
        String customerId,

        @NotEmpty
        @Valid
        List<OrderItemRequest> items
) {}
