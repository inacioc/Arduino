package com.example.ordermanagement.domain.validation;

/**
 * One broken functional rule.
 *
 * @param code         stable, machine-readable identifier of the rule (e.g. {@code PRICE_MISMATCH});
 *                     becomes {@code ApiError.code} at the web edge.
 * @param propertyPath what the rule was broken on - a property path or the business key of the
 *                     offending element; becomes an entry of {@code ApiError.errorsValueList}.
 * @param message      human-readable explanation of this specific violation.
 */
public record DomainViolation(String code, String propertyPath, String message) {

    public static final String DEFAULT_CODE = "DOMAIN_RULE_VIOLATION";

    public DomainViolation(String propertyPath, String message) {
        this(DEFAULT_CODE, propertyPath, message);
    }
}
