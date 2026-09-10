package com.example.ordermanagement.domain.exception;

/**
 * The request conflicts with a resource that already exists — a business
 * duplicate (e.g. a product name that must be unique). {@link #getResourceName()}
 * + {@link #getIdentifier()} give {@code GlobalExceptionHandler} everything it
 * needs to build a machine-readable {@code ApiError.code} (e.g.
 * {@code "PRODUCT_ALREADY_EXISTS"}) — see {@link ProductAlreadyExistsException}.
 * <p>
 * Not to be confused with an aggregate rejecting an illegal state transition
 * ({@code Order.confirm()} on an already-confirmed order): that stays a plain
 * {@link IllegalStateException} per this project's convention (see
 * {@code CLAUDE.md}) — it's an existing resource in the wrong state, not a new
 * one conflicting with an existing one.
 */
public abstract class ResourceAlreadyExistsException extends DomainException {

    private final String resourceName;
    private final String identifier;

    protected ResourceAlreadyExistsException(String resourceName, String identifier) {
        super(String.format("%s with identifier '%s' already exists.", resourceName, identifier));
        this.resourceName = resourceName;
        this.identifier = identifier;
    }

    public String getResourceName() {
        return resourceName;
    }

    public String getIdentifier() {
        return identifier;
    }
}
