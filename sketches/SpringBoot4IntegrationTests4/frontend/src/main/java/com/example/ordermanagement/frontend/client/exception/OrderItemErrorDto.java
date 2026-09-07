package com.example.ordermanagement.frontend.client.exception;

import java.util.UUID;

/**
 * One line-level error out of adapter-in-web's 422 response body for
 * {@code OrderDomainService.OrderValidationException}: {@code {productId, code, message}}.
 */
public record OrderItemErrorDto(UUID productId, String code, String message) {}
