package com.example.orders.infrastructure.out.client.dto;

public record InventoryResponse(
        String productId,
        boolean available,
        int quantity
) {}
