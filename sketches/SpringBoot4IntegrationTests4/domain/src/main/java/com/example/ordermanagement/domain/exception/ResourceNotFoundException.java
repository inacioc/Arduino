package com.example.ordermanagement.domain.exception;

/**
 * The caller asked for a resource by its identifier and it does not exist.
 * {@link #getResourceName()} + {@link #getIdentifier()} give
 * {@code GlobalExceptionHandler} everything it needs to build a machine-readable
 * {@code ApiError.code} (e.g. {@code "PRODUCT_NOT_FOUND"}) without every concrete
 * subtype having to spell one out itself — see {@link ProductNotFoundException}/
 * {@link OrderNotFoundException} for how thin that makes each concrete subtype.
 */
public abstract class ResourceNotFoundException extends DomainException {

    private final String resourceName;
    private final String identifier;

    protected ResourceNotFoundException(String resourceName, String identifier) {
        super(String.format("%s with identifier '%s' was not found.", resourceName, identifier));
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
