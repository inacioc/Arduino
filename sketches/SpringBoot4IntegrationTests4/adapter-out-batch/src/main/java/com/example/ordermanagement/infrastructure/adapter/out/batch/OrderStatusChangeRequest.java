package com.example.ordermanagement.infrastructure.adapter.out.batch;

/**
 * One input row of the status-change CSV: the order id and the desired target status.
 * Kept as raw strings so the processor can report parse problems as result codes
 * instead of failing the whole job on a malformed line.
 */
public record OrderStatusChangeRequest(
        String orderId,
        String targetStatus
) {}
