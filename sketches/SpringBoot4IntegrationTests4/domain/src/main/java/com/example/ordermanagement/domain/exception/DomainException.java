package com.example.ordermanagement.domain.exception;

/**
 * Root of every exception the domain layer (services, aggregates, and the ports
 * they drive) raises to signal that a use case cannot complete — whether the
 * reason is functional (a business rule, a missing resource) or technical (the
 * database or a downstream broker is unavailable).
 * <p>
 * Deliberately one hierarchy, not two: from a driving adapter's point of view
 * ({@code adapter-in-web}'s {@code GlobalExceptionHandler}), "the order doesn't
 * exist" and "the database is unreachable" are both just "something under
 * {@code DomainException} was thrown, map it to a {@code ResponseApiError}" —
 * the split that matters is in each subtype's semantics (functional vs.
 * technical), not in where it lives in the type tree. See {@code Validation.md}
 * for the full rationale and the concrete subtypes.
 * <p>
 * Knows nothing about HTTP, JSON, JPA, or any other transport/infrastructure
 * concern — that translation is entirely the adapters' job:
 * {@code adapter-out-persistence}'s {@code AbstractPersistenceAdapter} translates
 * {@code DataAccessException} into this hierarchy on the way out of the
 * database; {@code adapter-in-web}'s {@code GlobalExceptionHandler} translates
 * this hierarchy into {@code ResponseApiError} on the way out over HTTP.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
