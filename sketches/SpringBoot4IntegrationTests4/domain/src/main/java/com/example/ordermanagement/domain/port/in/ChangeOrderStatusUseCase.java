package com.example.ordermanagement.domain.port.in;

import java.util.UUID;

/**
 * Inbound port: advance a single order to a target status, chaining through any
 * intermediate states (e.g. {@code PENDING → COMPLETED} runs confirm → process →
 * complete).
 * <p>
 * Unlike {@link ProcessOrderUseCase}, this use case reports every <em>expected</em>
 * business outcome as data ({@link ChangeOrderStatusResult}) instead of throwing, so a
 * caller that processes many rows (the batch job) can record a per-row result without a
 * single bad row failing the rest. The rich {@code Order} aggregate never leaves the
 * hexagon — the result carries only the status name as a {@code String}.
 */
public interface ChangeOrderStatusUseCase {

    ChangeOrderStatusResult changeStatus(UUID orderId, String targetStatus);

    enum Outcome {
        /** The order was advanced (or already in the target status) and saved. */
        CHANGED,
        /** No order exists for the given id. */
        ORDER_NOT_FOUND,
        /** The target status string does not match any known status. */
        INVALID_TARGET_STATUS,
        /** The order cannot legally move from its current status to the target. */
        INVALID_TRANSITION
    }

    /**
     * @param orderId the order the change was attempted on
     * @param status  the order's status after the attempt — the new status on
     *                {@link Outcome#CHANGED}, the unchanged current status on
     *                {@link Outcome#INVALID_TRANSITION}, and {@code null} when the order
     *                or the target status could not be resolved
     * @param outcome what happened
     */
    record ChangeOrderStatusResult(UUID orderId, String status, Outcome outcome) {}
}
