package com.example.ordermanagement.infrastructure.adapter.in.web.error;

/**
 * How an {@link ApiError} should be treated — orthogonal to the response's HTTP
 * status, which says how the client should react; this says where the error
 * originated and how urgently it deserves operator attention.
 */
public enum ErrorLevel {

    /**
     * An ordinary, expected outcome of validation or a business rule: a missing
     * field, a product that doesn't exist, an order that can't transition, a
     * duplicate name. The client sent something that can't succeed as-is, but a
     * different request would. Not logged above INFO/DEBUG; never paged on.
     */
    BLOCKING,

    /**
     * The request was rejected not because of anything the client did, but
     * because infrastructure the domain depends on forced it to fail — the
     * database or a downstream broker is unavailable, a query timed out, a
     * constraint fired that isn't a simple business duplicate. Always logged
     * with full detail server-side (never in the response body) and is the
     * kind of error an operator should be alerted on if it recurs.
     */
    FROCED,

    /** A non-blocking notice attached to an otherwise-successful outcome (e.g. a deprecation notice). */
    INFORMATION
}
