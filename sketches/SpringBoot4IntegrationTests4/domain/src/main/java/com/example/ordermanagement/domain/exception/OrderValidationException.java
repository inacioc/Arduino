package com.example.ordermanagement.domain.exception;

import java.util.List;
import java.util.UUID;

/**
 * One or more order lines failed a functional/business rule — the referenced
 * product doesn't exist, isn't currently orderable, or was submitted at the
 * wrong price. Follows the Notification pattern: every bad line found is
 * collected and reported together in one exception (see {@link #getErrors()}),
 * rather than failing on the first one, so the caller sees every problem at
 * once instead of fixing lines one request at a time.
 * <p>
 * {@code GlobalExceptionHandler} groups {@link #getErrors()} by
 * {@link OrderItemErrorCode} into one {@code ApiError} per code, listing every
 * affected {@code productId} in that {@code ApiError}'s {@code errorsValueList}
 * — see {@code Validation.md}.
 */
public class OrderValidationException extends DomainException {

    /** Machine-readable code for a single invalid order line. Extend as new rules appear. */
    public enum OrderItemErrorCode {
        PRODUCT_NOT_FOUND,
        PRODUCT_NOT_AVAILABLE,
        PRICE_MISMATCH
    }

    /** One collected validation error, tied to the offending product line. */
    public record OrderItemError(UUID productId, OrderItemErrorCode code, String message) {}

    private final transient List<OrderItemError> errors;

    public OrderValidationException(List<OrderItemError> errors) {
        super("Order validation failed with " + errors.size() + " error(s)");
        this.errors = List.copyOf(errors);
    }

    public List<OrderItemError> getErrors() {
        return errors;
    }
}
