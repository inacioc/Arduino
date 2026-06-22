package com.example.ordermanagement.infrastructure.adapter.out.batch;

/**
 * One output row of the result CSV.
 *
 * @param id            the order id from the input row
 * @param presentStatus the order's status after the attempt (unchanged on failure,
 *                      empty when the order/id could not be resolved)
 * @param result        {@code "OK"} on success, otherwise an error code
 */
public record OrderStatusChangeResult(
        String id,
        String presentStatus,
        String result
) {
    public static final String OK                    = "OK";
    public static final String INVALID_ID            = "INVALID_ID";
    public static final String INVALID_TARGET_STATUS = "INVALID_TARGET_STATUS";
    public static final String ORDER_NOT_FOUND       = "ORDER_NOT_FOUND";
    public static final String INVALID_TRANSITION    = "INVALID_TRANSITION";
}
