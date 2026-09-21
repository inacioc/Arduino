package com.example.ordermanagement.domain.exception;

import com.example.ordermanagement.domain.validation.DomainViolation;

import java.util.List;

/**
 * One or more functional rules were broken. Carries every {@link DomainViolation} collected,
 * so the caller fixes everything in one round trip.
 * <p>
 * {@code GlobalExceptionHandler} groups {@link #getViolations()} by code into one
 * {@code ApiError} per code (HTTP 422) - see {@code Validation.md}.
 */
public class DomainValidationException extends DomainException {

    private final transient List<DomainViolation> violations;

    public DomainValidationException(List<DomainViolation> violations) {
        super("Domain validation failed with " + violations.size() + " violation(s).");
        this.violations = List.copyOf(violations);
    }

    public List<DomainViolation> getViolations() {
        return violations;
    }
}
