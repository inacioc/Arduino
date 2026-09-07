package com.example.ordermanagement.frontend.client.exception;

import java.util.List;

/**
 * adapter-in-web returned 422 - its domain-level
 * {@code OrderDomainService.OrderValidationException} (e.g. a product referenced
 * by an order line no longer exists or was marked unavailable between the form
 * being rendered and submitted). Carries every failing line at once, same as the
 * domain exception does, so the controller can map each one back onto the
 * specific {@code items[i].productId} it came from.
 */
public class BackendOrderValidationException extends BackendApiException {

    private final List<OrderItemErrorDto> errors;

    public BackendOrderValidationException(String message, List<OrderItemErrorDto> errors) {
        super(message);
        this.errors = errors;
    }

    public List<OrderItemErrorDto> getErrors() {
        return errors;
    }
}
