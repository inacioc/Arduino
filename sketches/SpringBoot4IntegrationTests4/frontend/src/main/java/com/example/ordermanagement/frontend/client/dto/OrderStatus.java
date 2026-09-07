package com.example.ordermanagement.frontend.client.dto;

/**
 * Mirrors the {@code OrderStatus} values adapter-in-web serializes on the wire.
 * This is a copy of the API contract, not a reference to the domain enum -
 * frontend has no dependency on order-domain.
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    PROCESSING,
    COMPLETED,
    CANCELLED
}
