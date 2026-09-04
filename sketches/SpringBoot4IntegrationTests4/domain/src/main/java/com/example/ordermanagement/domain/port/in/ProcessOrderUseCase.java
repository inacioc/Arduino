package com.example.ordermanagement.domain.port.in;

import com.example.ordermanagement.domain.model.Order;
import com.example.ordermanagement.domain.model.OrderStatus;

import java.util.UUID;

public interface ProcessOrderUseCase {

    Order confirmOrder(UUID orderId);

    Order completeOrder(UUID orderId);

    Order cancelOrder(UUID orderId);

    /**
     * Advances the order to {@code target}, walking through intermediate states as
     * needed (see {@link com.example.ordermanagement.domain.model.Order#advanceTo}).
     * Unlike {@link #confirmOrder}/{@link #completeOrder}/{@link #cancelOrder}, this
     * accepts an arbitrary target and is meant for callers (e.g. bulk/batch status
     * updates) that don't know in advance which specific transition applies.
     */
    Order changeStatus(UUID orderId, OrderStatus target);
}
